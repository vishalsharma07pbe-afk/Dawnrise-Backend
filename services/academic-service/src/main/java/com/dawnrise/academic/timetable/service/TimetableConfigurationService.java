package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.*;
import com.dawnrise.academic.timetable.exception.InvalidTimetableConfigurationException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationConflictException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class TimetableConfigurationService {
    private final JdbcClient jdbc;
    private final IdentityTeacherEligibilityClient teacherEligibilityClient;
    private final SchoolTimeZoneClient schoolTimeZoneClient;

    public TimetableConfigurationService(JdbcClient jdbc,
                                         IdentityTeacherEligibilityClient teacherEligibilityClient,
                                         SchoolTimeZoneClient schoolTimeZoneClient) {
        this.jdbc = jdbc;
        this.teacherEligibilityClient = teacherEligibilityClient;
        this.schoolTimeZoneClient = schoolTimeZoneClient;
    }

    @Transactional(readOnly = true)
    public List<WingResponse> wings(long organizationId) {
        return jdbc.sql("""
                SELECT id, code, name, active, display_order, version
                FROM timetable_wings WHERE organization_id = :org ORDER BY display_order, id
                """).param("org", organizationId).query((rs, n) -> new WingResponse(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getBoolean("active"),
                rs.getInt("display_order"), rs.getLong("version"))).list();
    }

    @Transactional
    public WingResponse createWing(long organizationId, long actorId, WingRequest request) {
        try {
            long id = jdbc.sql("""
                    INSERT INTO timetable_wings (organization_id, code, name, active, display_order)
                    VALUES (:org, :code, :name, :active, :displayOrder) RETURNING id
                    """).param("org", organizationId).param("code", normalizeCode(request.code()))
                    .param("name", request.name().trim()).param("active", request.active())
                    .param("displayOrder", request.displayOrder()).query(Long.class).single();
            audit(organizationId, actorId, "CREATE", "WING", id);
            return wing(organizationId, id);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Wing code or display order is already in use", ex);
        }
    }

    @Transactional
    public WingResponse updateWing(long organizationId, long actorId, long id, WingRequest request) {
        requireVersion(request.expectedVersion());
        int changed;
        try {
            changed = jdbc.sql("""
                    UPDATE timetable_wings SET code=:code, name=:name, active=:active,
                        display_order=:displayOrder, version=version+1, updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("code", normalizeCode(request.code())).param("name", request.name().trim())
                    .param("active", request.active()).param("displayOrder", request.displayOrder())
                    .param("id", id).param("org", organizationId).param("version", request.expectedVersion()).update();
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Wing code or display order is already in use", ex);
        }
        ensureChanged(changed, "Wing", id, organizationId);
        audit(organizationId, actorId, "UPDATE", "WING", id);
        return wing(organizationId, id);
    }

    @Transactional
    public GradeWingResponse assignGradeWing(long organizationId, long actorId, GradeWingRequest request) {
        assertGrade(organizationId, request.academicYearId(), request.gradeLevelId());
        assertExists("timetable_wings", organizationId, request.wingId(), "Wing");
        Long existingId = jdbc.sql("""
                SELECT id FROM timetable_grade_wings WHERE organization_id=:org
                AND academic_year_id=:year AND grade_level_id=:grade
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("grade", request.gradeLevelId()).query(Long.class).optional().orElse(null);
        long id;
        if (existingId == null) {
            id = jdbc.sql("""
                    INSERT INTO timetable_grade_wings
                    (organization_id, academic_year_id, grade_level_id, wing_id)
                    VALUES (:org,:year,:grade,:wing) RETURNING id
                    """).param("org", organizationId).param("year", request.academicYearId())
                    .param("grade", request.gradeLevelId()).param("wing", request.wingId()).query(Long.class).single();
        } else {
            requireVersion(request.expectedVersion());
            assertGradeWingChangeIsConsistent(organizationId, request.academicYearId(),
                    request.gradeLevelId(), request.wingId());
            int changed = jdbc.sql("""
                    UPDATE timetable_grade_wings SET wing_id=:wing, version=version+1, updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("wing", request.wingId()).param("id", existingId).param("org", organizationId)
                    .param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Grade-wing mapping", existingId, organizationId);
            id = existingId;
        }
        audit(organizationId, actorId, existingId == null ? "CREATE" : "UPDATE", "GRADE_WING", id);
        return jdbc.sql("""
                SELECT id, academic_year_id, grade_level_id, wing_id, version
                FROM timetable_grade_wings WHERE id=:id AND organization_id=:org
                """).param("id", id).param("org", organizationId).query((rs, n) -> new GradeWingResponse(
                rs.getLong("id"), rs.getLong("academic_year_id"), rs.getLong("grade_level_id"),
                rs.getLong("wing_id"), rs.getLong("version"))).single();
    }

    @Transactional(readOnly = true)
    public List<BellScheduleResponse> bellSchedules(long organizationId) {
        return jdbc.sql("SELECT id FROM timetable_bell_schedules WHERE organization_id=:org ORDER BY name, id")
                .param("org", organizationId).query(Long.class).list().stream()
                .map(id -> bellSchedule(organizationId, id)).toList();
    }

    @Transactional
    public BellScheduleResponse createBellSchedule(long organizationId, long actorId,
                                                   BellScheduleRequest request) {
        validateDays(request.days());
        long id;
        try {
            id = jdbc.sql("""
                    INSERT INTO timetable_bell_schedules (organization_id,name,description,active)
                    VALUES (:org,:name,:description,:active) RETURNING id
                    """).param("org", organizationId).param("name", request.name().trim())
                    .param("description", trimToNull(request.description())).param("active", request.active())
                    .query(Long.class).single();
            replaceDays(organizationId, id, request.days());
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Bell schedule name, day, interval order, or time range conflicts", ex);
        }
        audit(organizationId, actorId, "CREATE", "BELL_SCHEDULE", id);
        return bellSchedule(organizationId, id);
    }

    @Transactional
    public BellScheduleResponse updateBellSchedule(long organizationId, long actorId, long id,
                                                   BellScheduleRequest request) {
        requireVersion(request.expectedVersion());
        validateDays(request.days());
        lock("timetable_bell_schedules", organizationId, id, "Bell schedule");
        int changed;
        try {
            changed = jdbc.sql("""
                    UPDATE timetable_bell_schedules SET name=:name, description=:description, active=:active,
                    version=version+1, updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("name", request.name().trim()).param("description", trimToNull(request.description()))
                    .param("active", request.active()).param("id", id).param("org", organizationId)
                    .param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Bell schedule", id, organizationId);
            jdbc.sql("DELETE FROM timetable_bell_schedule_days WHERE organization_id=:org AND bell_schedule_id=:id")
                    .param("org", organizationId).param("id", id).update();
            replaceDays(organizationId, id, request.days());
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Bell schedule name, day, interval order, or time range conflicts", ex);
        }
        audit(organizationId, actorId, "UPDATE", "BELL_SCHEDULE", id);
        return bellSchedule(organizationId, id);
    }

    @Transactional
    public ScheduleAssignmentResponse assignSchedule(long organizationId, long actorId,
                                                     ScheduleAssignmentRequest request) {
        assertYear(organizationId, request.academicYearId());
        assertExists("timetable_wings", organizationId, request.wingId(), "Wing");
        assertExists("timetable_bell_schedules", organizationId, request.bellScheduleId(), "Bell schedule");
        if (request.gradeLevelId() != null) {
            assertGrade(organizationId, request.academicYearId(), request.gradeLevelId());
            Long mappedWing = jdbc.sql("""
                    SELECT wing_id FROM timetable_grade_wings WHERE organization_id=:org
                    AND academic_year_id=:year AND grade_level_id=:grade
                    """).param("org", organizationId).param("year", request.academicYearId())
                    .param("grade", request.gradeLevelId()).query(Long.class).optional().orElse(null);
            if (mappedWing == null || mappedWing != request.wingId()) {
                throw new InvalidTimetableConfigurationException("Grade override must use the grade's configured wing");
            }
        }
        Long existing = jdbc.sql("""
                SELECT id FROM timetable_bell_schedule_assignments WHERE organization_id=:org
                AND academic_year_id=:year AND wing_id=:wing
                AND grade_level_id IS NOT DISTINCT FROM :grade
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("wing", request.wingId()).param("grade", request.gradeLevelId())
                .query(Long.class).optional().orElse(null);
        long id;
        if (existing == null) {
            id = jdbc.sql("""
                    INSERT INTO timetable_bell_schedule_assignments
                    (organization_id,academic_year_id,wing_id,grade_level_id,bell_schedule_id)
                    VALUES (:org,:year,:wing,:grade,:schedule) RETURNING id
                    """).param("org", organizationId).param("year", request.academicYearId())
                    .param("wing", request.wingId()).param("grade", request.gradeLevelId())
                    .param("schedule", request.bellScheduleId()).query(Long.class).single();
        } else {
            requireVersion(request.expectedVersion());
            int changed = jdbc.sql("""
                    UPDATE timetable_bell_schedule_assignments SET bell_schedule_id=:schedule,
                    version=version+1, updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("schedule", request.bellScheduleId()).param("id", existing)
                    .param("org", organizationId).param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Bell schedule assignment", existing, organizationId);
            id = existing;
        }
        audit(organizationId, actorId, existing == null ? "CREATE" : "UPDATE", "SCHEDULE_ASSIGNMENT", id);
        return scheduleAssignment(organizationId, id);
    }

    @Transactional
    public SubjectRequirementResponse saveRequirement(long organizationId, long actorId,
                                                      SubjectRequirementRequest request) {
        resolvedSchedule(organizationId, request.academicYearId(), request.sectionId());
        assertSectionSubject(organizationId, request);
        validateRequirementAgainstSchedule(organizationId, request);
        Long existing = jdbc.sql("""
                SELECT id FROM timetable_section_subject_requirements WHERE organization_id=:org
                AND academic_year_id=:year AND section_id=:section AND grade_level_subject_id=:subject
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("section", request.sectionId()).param("subject", request.gradeLevelSubjectId())
                .query(Long.class).optional().orElse(null);
        long id;
        if (existing == null) {
            id = jdbc.sql("""
                    INSERT INTO timetable_section_subject_requirements
                    (organization_id,academic_year_id,grade_level_id,section_id,grade_level_subject_id,
                     periods_per_week,required_each_working_day,maximum_periods_per_day,
                     double_period_blocks_per_week,allow_consecutive_periods)
                    VALUES (:org,:year,:grade,:section,:subject,:weekly,:daily,:maximum,:doubles,:allowed) RETURNING id
                    """).param("org", organizationId).param("year", request.academicYearId())
                    .param("grade", request.gradeLevelId()).param("section", request.sectionId())
                    .param("subject", request.gradeLevelSubjectId()).param("weekly", request.periodsPerWeek())
                    .param("daily", request.requiredEachWorkingDay()).param("maximum", request.maximumPeriodsPerDay())
                    .param("doubles", request.doublePeriodBlocksPerWeek())
                    .param("allowed", request.allowConsecutivePeriods()).query(Long.class).single();
        } else {
            requireVersion(request.expectedVersion());
            int changed = jdbc.sql("""
                    UPDATE timetable_section_subject_requirements SET periods_per_week=:weekly,
                    required_each_working_day=:daily, maximum_periods_per_day=:maximum,
                    double_period_blocks_per_week=:doubles, allow_consecutive_periods=:allowed,
                    version=version+1, updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("weekly", request.periodsPerWeek()).param("daily", request.requiredEachWorkingDay())
                    .param("maximum", request.maximumPeriodsPerDay()).param("doubles", request.doublePeriodBlocksPerWeek())
                    .param("allowed", request.allowConsecutivePeriods())
                    .param("id", existing).param("org", organizationId).param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Subject requirement", existing, organizationId);
            id = existing;
        }
        audit(organizationId, actorId, existing == null ? "CREATE" : "UPDATE", "SUBJECT_REQUIREMENT", id);
        return requirement(organizationId, id);
    }

    @Transactional
    public long saveTeacherPolicy(long organizationId, long actorId, TeacherPolicyRequest request) {
        assertYear(organizationId, request.academicYearId());
        validateEligibleTeacher(organizationId, request.teacherUserId());
        Long existing = findPolicy(organizationId, request.academicYearId(), request.teacherUserId());
        long id;
        if (existing == null) {
            id = jdbc.sql("""
                    INSERT INTO timetable_teacher_policies
                    (organization_id,academic_year_id,teacher_user_id,maximum_periods_per_day,
                     maximum_periods_per_week,maximum_consecutive_periods,minimum_rest_minutes)
                    VALUES (:org,:year,:teacher,:daily,:weekly,:consecutive,:rest) RETURNING id
                    """).param("org", organizationId).param("year", request.academicYearId())
                    .param("teacher", request.teacherUserId()).param("daily", request.maximumPeriodsPerDay())
                    .param("weekly", request.maximumPeriodsPerWeek()).param("consecutive", request.maximumConsecutivePeriods())
                    .param("rest", request.minimumRestMinutes()).query(Long.class).single();
        } else {
            requireVersion(request.expectedVersion());
            int changed = jdbc.sql("""
                    UPDATE timetable_teacher_policies SET maximum_periods_per_day=:daily,
                    maximum_periods_per_week=:weekly, maximum_consecutive_periods=:consecutive,
                    minimum_rest_minutes=:rest, version=version+1, updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("daily", request.maximumPeriodsPerDay()).param("weekly", request.maximumPeriodsPerWeek())
                    .param("consecutive", request.maximumConsecutivePeriods()).param("rest", request.minimumRestMinutes())
                    .param("id", existing).param("org", organizationId).param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Teacher policy", existing, organizationId);
            id = existing;
        }
        audit(organizationId, actorId, existing == null ? "CREATE" : "UPDATE", "TEACHER_POLICY", id);
        return id;
    }

    @Transactional
    public long addTeacherAvailability(long organizationId, long actorId, TeacherAvailabilityRequest request) {
        assertYear(organizationId, request.academicYearId());
        validateEligibleTeacher(organizationId, request.teacherUserId());
        TimetableConfigurationRules.validateAvailabilityTimes(request.startTime(), request.endTime());
        long overlaps = jdbc.sql("""
                SELECT COUNT(*) FROM timetable_teacher_availability
                WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher
                  AND day_of_week=:day AND start_time < :end AND end_time > :start
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("teacher", request.teacherUserId()).param("day", request.dayOfWeek())
                .param("start", request.startTime()).param("end", request.endTime()).query(Long.class).single();
        if (overlaps > 0) {
            throw new TimetableConfigurationConflictException(
                    "Teacher availability rules cannot overlap; split or remove the existing rule first");
        }
        long id = jdbc.sql("""
                INSERT INTO timetable_teacher_availability
                (organization_id,academic_year_id,teacher_user_id,day_of_week,start_time,end_time,availability_type,reason)
                VALUES (:org,:year,:teacher,:day,:start,:end,:type,:reason) RETURNING id
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("teacher", request.teacherUserId()).param("day", request.dayOfWeek())
                .param("start", request.startTime()).param("end", request.endTime())
                .param("type", request.availabilityType().name()).param("reason", trimToNull(request.reason()))
                .query(Long.class).single();
        audit(organizationId, actorId, "CREATE", "TEACHER_AVAILABILITY", id);
        return id;
    }

    @Transactional
    public TeacherAvailabilityResponse updateTeacherAvailability(long organizationId, long actorId, long id,
                                                                  TeacherAvailabilityUpdateRequest request) {
        TimetableConfigurationRules.validateAvailabilityTimes(request.startTime(), request.endTime());
        var current = teacherAvailability(organizationId, id, true);
        long overlaps = jdbc.sql("""
                SELECT COUNT(*) FROM timetable_teacher_availability
                WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher
                  AND day_of_week=:day AND id<>:id AND start_time < :end AND end_time > :start
                """).param("org", organizationId).param("year", current.academicYearId())
                .param("teacher", current.teacherUserId()).param("day", request.dayOfWeek()).param("id", id)
                .param("start", request.startTime()).param("end", request.endTime()).query(Long.class).single();
        if (overlaps > 0) throw new TimetableConfigurationConflictException(
                "Teacher availability rules cannot overlap; split or remove the existing rule first");
        int changed = jdbc.sql("""
                UPDATE timetable_teacher_availability SET day_of_week=:day,start_time=:start,end_time=:end,
                    availability_type=:type,reason=:reason,version=version+1
                WHERE id=:id AND organization_id=:org AND version=:version
                """).param("day", request.dayOfWeek()).param("start", request.startTime())
                .param("end", request.endTime()).param("type", request.availabilityType().name())
                .param("reason", trimToNull(request.reason())).param("id", id).param("org", organizationId)
                .param("version", request.expectedVersion()).update();
        if (changed != 1) throw new TimetableConfigurationConflictException(
                "Teacher availability was modified by another request; reload and retry");
        audit(organizationId, actorId, "UPDATE", "TEACHER_AVAILABILITY", id);
        return teacherAvailability(organizationId, id, false);
    }

    @Transactional
    public void deleteTeacherAvailability(long organizationId, long actorId, long id, long expectedVersion) {
        teacherAvailability(organizationId, id, true);
        int changed = jdbc.sql("""
                DELETE FROM timetable_teacher_availability
                WHERE id=:id AND organization_id=:org AND version=:version
                """).param("id", id).param("org", organizationId).param("version", expectedVersion).update();
        if (changed != 1) throw new TimetableConfigurationConflictException(
                "Teacher availability was modified by another request; reload and retry");
        audit(organizationId, actorId, "DELETE", "TEACHER_AVAILABILITY", id);
    }

    private TeacherAvailabilityResponse teacherAvailability(long organizationId, long id, boolean lock) {
        return jdbc.sql("""
                SELECT * FROM timetable_teacher_availability WHERE id=:id AND organization_id=:org
                """ + (lock ? " FOR UPDATE" : ""))
                .param("id", id).param("org", organizationId).query((rs,n) -> new TeacherAvailabilityResponse(
                        rs.getLong("id"),rs.getLong("academic_year_id"),rs.getLong("teacher_user_id"),
                        rs.getInt("day_of_week"),rs.getObject("start_time",LocalTime.class),
                        rs.getObject("end_time",LocalTime.class),AvailabilityType.valueOf(rs.getString("availability_type")),
                        rs.getString("reason"),rs.getLong("version"))).optional()
                .orElseThrow(() -> new TimetableConfigurationNotFoundException("Teacher availability not found"));
    }

    @Transactional
    public long saveAlternateEligibility(long organizationId, long actorId,
                                         AlternateEligibilityRequest request) {
        validateEligibleTeacher(organizationId, request.teacherUserId());
        if (request.validFrom() != null && request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new InvalidTimetableConfigurationException("Eligibility valid-to date cannot precede valid-from date");
        }
        assertGradeSubject(organizationId, request.academicYearId(), request.gradeLevelId(), request.gradeLevelSubjectId());
        if (request.sectionId() != null) assertSection(organizationId, request.academicYearId(), request.gradeLevelId(), request.sectionId());
        if (request.wingId() != null) assertExists("timetable_wings", organizationId, request.wingId(), "Wing");
        assertEligibilitySectionWingAgreement(organizationId, request.academicYearId(), request.gradeLevelId(),
                request.sectionId(), request.wingId());
        Long existing = jdbc.sql("""
                SELECT id FROM timetable_alternate_teacher_eligibilities WHERE organization_id=:org
                AND academic_year_id=:year AND teacher_user_id=:teacher AND grade_level_subject_id=:subject
                AND section_id IS NOT DISTINCT FROM :section AND wing_id IS NOT DISTINCT FROM :wing
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("teacher", request.teacherUserId()).param("subject", request.gradeLevelSubjectId())
                .param("section", request.sectionId()).param("wing", request.wingId())
                .query(Long.class).optional().orElse(null);
        long id;
        if (existing == null) {
            id = jdbc.sql("""
                    INSERT INTO timetable_alternate_teacher_eligibilities
                    (organization_id,academic_year_id,teacher_user_id,grade_level_subject_id,grade_level_id,
                     section_id,wing_id,eligibility_type,valid_from,valid_to,active,approved_by_user_id)
                    VALUES (:org,:year,:teacher,:subject,:grade,:section,:wing,:type,:from,:to,:active,:actor) RETURNING id
                    """).param("org", organizationId).param("year", request.academicYearId())
                    .param("teacher", request.teacherUserId()).param("subject", request.gradeLevelSubjectId())
                    .param("grade", request.gradeLevelId()).param("section", request.sectionId()).param("wing", request.wingId())
                    .param("type", request.eligibilityType().name()).param("from", request.validFrom()).param("to", request.validTo())
                    .param("active", request.active()).param("actor", actorId).query(Long.class).single();
        } else {
            requireVersion(request.expectedVersion());
            int changed = jdbc.sql("""
                    UPDATE timetable_alternate_teacher_eligibilities SET eligibility_type=:type,
                    valid_from=:from, valid_to=:to, active=:active, approved_by_user_id=:actor,
                    version=version+1, updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("type", request.eligibilityType().name()).param("from", request.validFrom())
                    .param("to", request.validTo()).param("active", request.active()).param("actor", actorId)
                    .param("id", existing).param("org", organizationId).param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Alternate teacher eligibility", existing, organizationId);
            id = existing;
        }
        audit(organizationId, actorId, existing == null ? "CREATE" : "UPDATE", "ALTERNATE_ELIGIBILITY", id);
        return id;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> rooms(long organizationId) {
        return jdbc.sql("SELECT id FROM timetable_rooms WHERE organization_id=:org ORDER BY code,id")
                .param("org", organizationId).query(Long.class).list().stream().map(id -> room(organizationId, id)).toList();
    }

    @Transactional(readOnly = true)
    public AcademicYearConfiguration academicYearConfiguration(long organizationId, long academicYearId) {
        assertYear(organizationId, academicYearId);
        List<GradeWingResponse> gradeWings = jdbc.sql("""
                SELECT id,academic_year_id,grade_level_id,wing_id,version FROM timetable_grade_wings
                WHERE organization_id=:org AND academic_year_id=:year ORDER BY grade_level_id
                """).param("org", organizationId).param("year", academicYearId).query((rs,n) -> new GradeWingResponse(
                rs.getLong("id"), rs.getLong("academic_year_id"), rs.getLong("grade_level_id"),
                rs.getLong("wing_id"), rs.getLong("version"))).list();
        List<ScheduleAssignmentResponse> assignments = jdbc.sql("""
                SELECT id,academic_year_id,wing_id,grade_level_id,bell_schedule_id,version
                FROM timetable_bell_schedule_assignments WHERE organization_id=:org AND academic_year_id=:year
                ORDER BY wing_id,grade_level_id NULLS FIRST
                """).param("org", organizationId).param("year", academicYearId).query((rs,n) -> new ScheduleAssignmentResponse(
                rs.getLong("id"), rs.getLong("academic_year_id"), rs.getLong("wing_id"),
                (Long) rs.getObject("grade_level_id"), rs.getLong("bell_schedule_id"), rs.getLong("version"))).list();
        List<SubjectRequirementResponse> requirements = jdbc.sql("""
                SELECT * FROM timetable_section_subject_requirements WHERE organization_id=:org AND academic_year_id=:year
                ORDER BY section_id,grade_level_subject_id
                """).param("org", organizationId).param("year", academicYearId).query((rs,n) -> new SubjectRequirementResponse(
                rs.getLong("id"),rs.getLong("academic_year_id"),rs.getLong("grade_level_id"),rs.getLong("section_id"),
                rs.getLong("grade_level_subject_id"),rs.getInt("periods_per_week"),rs.getBoolean("required_each_working_day"),
                rs.getInt("maximum_periods_per_day"),rs.getInt("double_period_blocks_per_week"),
                rs.getBoolean("allow_consecutive_periods"),rs.getLong("version"))).list();
        List<TeacherPolicyResponse> policies = jdbc.sql("""
                SELECT * FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year ORDER BY teacher_user_id
                """).param("org", organizationId).param("year", academicYearId).query((rs,n) -> new TeacherPolicyResponse(
                rs.getLong("id"),rs.getLong("academic_year_id"),rs.getLong("teacher_user_id"),
                rs.getInt("maximum_periods_per_day"),rs.getInt("maximum_periods_per_week"),
                rs.getInt("maximum_consecutive_periods"),rs.getInt("minimum_rest_minutes"),rs.getLong("version"))).list();
        List<TeacherAvailabilityResponse> availability = jdbc.sql("""
                SELECT * FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year
                ORDER BY teacher_user_id,day_of_week,start_time
                """).param("org", organizationId).param("year", academicYearId).query((rs,n) -> new TeacherAvailabilityResponse(
                rs.getLong("id"),rs.getLong("academic_year_id"),rs.getLong("teacher_user_id"),rs.getInt("day_of_week"),
                rs.getObject("start_time",LocalTime.class),rs.getObject("end_time",LocalTime.class),
                AvailabilityType.valueOf(rs.getString("availability_type")),rs.getString("reason"),rs.getLong("version"))).list();
        List<AlternateEligibilityResponse> eligibilities = jdbc.sql("""
                SELECT * FROM timetable_alternate_teacher_eligibilities WHERE organization_id=:org AND academic_year_id=:year
                ORDER BY grade_level_subject_id,teacher_user_id
                """).param("org", organizationId).param("year", academicYearId).query((rs,n) -> new AlternateEligibilityResponse(
                rs.getLong("id"),rs.getLong("academic_year_id"),rs.getLong("teacher_user_id"),
                rs.getLong("grade_level_subject_id"),rs.getLong("grade_level_id"),(Long)rs.getObject("section_id"),
                (Long)rs.getObject("wing_id"),EligibilityType.valueOf(rs.getString("eligibility_type")),
                rs.getObject("valid_from",java.time.LocalDate.class),rs.getObject("valid_to",java.time.LocalDate.class),
                rs.getBoolean("active"),rs.getLong("approved_by_user_id"),rs.getLong("version"))).list();
        List<SectionHomeRoomResponse> homeRooms = jdbc.sql("""
                SELECT h.id,h.academic_year_id,s.grade_level_id,h.section_id,h.room_id,h.version
                FROM timetable_section_home_rooms h JOIN sections s ON s.id=h.section_id
                    AND s.academic_year_id=h.academic_year_id AND s.organization_id=h.organization_id
                WHERE h.organization_id=:org AND h.academic_year_id=:year ORDER BY s.grade_level_id,h.section_id
                """).param("org", organizationId).param("year", academicYearId).query((rs,n) -> new SectionHomeRoomResponse(
                rs.getLong("id"),rs.getLong("academic_year_id"),rs.getLong("grade_level_id"),
                rs.getLong("section_id"),rs.getLong("room_id"),rs.getLong("version"))).list();
        List<SubjectRoomRequirementResponse> roomRequirements = jdbc.sql("""
                SELECT * FROM timetable_subject_room_requirements WHERE organization_id=:org AND academic_year_id=:year
                ORDER BY grade_level_subject_id,capability_code
                """).param("org", organizationId).param("year", academicYearId).query((rs,n) -> new SubjectRoomRequirementResponse(
                rs.getLong("id"),rs.getLong("academic_year_id"),rs.getLong("grade_level_id"),
                rs.getLong("grade_level_subject_id"),rs.getString("capability_code"),rs.getBoolean("required"),rs.getLong("version"))).list();
        return new AcademicYearConfiguration(academicYearId, gradeWings, assignments, requirements, policies,
                availability, eligibilities, homeRooms, roomRequirements);
    }

    @Transactional(readOnly = true)
    public ConfigurationOptions configurationOptions(long organizationId, long academicYearId) {
        assertYear(organizationId, academicYearId);
        List<LabeledReference> wingOptions = jdbc.sql("""
                SELECT id,name FROM timetable_wings WHERE organization_id=:org ORDER BY display_order,id
                """).param("org", organizationId).query((rs,n) -> new LabeledReference(rs.getLong(1),rs.getString(2))).list();
        List<LabeledReference> gradeOptions = jdbc.sql("""
                SELECT id,name FROM grade_levels WHERE organization_id=:org AND academic_year_id=:year
                ORDER BY display_order,id
                """).param("org", organizationId).param("year", academicYearId)
                .query((rs,n) -> new LabeledReference(rs.getLong(1),rs.getString(2))).list();
        List<LabeledReference> sectionOptions = jdbc.sql("""
                SELECT id,name FROM sections WHERE organization_id=:org AND academic_year_id=:year
                ORDER BY grade_level_id,display_order,id
                """).param("org", organizationId).param("year", academicYearId)
                .query((rs,n) -> new LabeledReference(rs.getLong(1),rs.getString(2))).list();
        List<LabeledReference> subjectOptions = jdbc.sql("""
                SELECT gs.id,s.name FROM grade_level_subjects gs JOIN subjects s
                  ON s.id=gs.subject_id AND s.organization_id=gs.organization_id
                WHERE gs.organization_id=:org AND gs.academic_year_id=:year
                ORDER BY gs.grade_level_id,gs.display_order,gs.id
                """).param("org", organizationId).param("year", academicYearId)
                .query((rs,n) -> new LabeledReference(rs.getLong(1),rs.getString(2))).list();
        List<LabeledReference> roomOptions = jdbc.sql("""
                SELECT id,name FROM timetable_rooms WHERE organization_id=:org ORDER BY code,id
                """).param("org", organizationId).query((rs,n) -> new LabeledReference(rs.getLong(1),rs.getString(2))).list();
        List<Long> teacherIds = jdbc.sql("""
                SELECT DISTINCT teacher_user_id FROM (
                    SELECT teacher_user_id FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year
                    UNION SELECT teacher_user_id FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year
                    UNION SELECT teacher_user_id FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year
                    UNION SELECT teacher_user_id FROM timetable_alternate_teacher_eligibilities WHERE organization_id=:org AND academic_year_id=:year
                ) teachers ORDER BY teacher_user_id
                """).param("org", organizationId).param("year", academicYearId).query(Long.class).list();
        List<TeacherReference> teacherOptions = teacherIds.isEmpty() ? List.of() :
                teacherEligibilityClient.checkBatch(organizationId, teacherIds).results().stream()
                        .filter(item -> item != null && item.userId() != null
                                && Objects.equals(item.organizationId(), organizationId) && teacherIds.contains(item.userId()))
                        .map(item -> new TeacherReference(item.userId(),
                                item.displayName() == null || item.displayName().isBlank()
                                        ? "Teacher #" + item.userId() : item.displayName().trim()))
                        .sorted(java.util.Comparator.comparing(TeacherReference::displayName)
                                .thenComparingLong(TeacherReference::id)).toList();
        return new ConfigurationOptions(wingOptions, gradeOptions, sectionOptions, subjectOptions,
                roomOptions, teacherOptions);
    }

    @Transactional
    public RoomResponse createRoom(long organizationId, long actorId, RoomRequest request) {
        validateRoomWing(organizationId, request.wingId());
        long id;
        try {
            id = jdbc.sql("""
                    INSERT INTO timetable_rooms (organization_id,wing_id,code,name,capacity,exclusive_use,active)
                    VALUES (:org,:wing,:code,:name,:capacity,:exclusive,:active) RETURNING id
                    """).param("org", organizationId).param("wing", request.wingId())
                    .param("code", normalizeCode(request.code())).param("name", request.name().trim())
                    .param("capacity", request.capacity()).param("exclusive", request.exclusiveUse())
                    .param("active", request.active()).query(Long.class).single();
            replaceCapabilities(organizationId, id, request.capabilities());
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Room code or capability conflicts with existing configuration", ex);
        }
        audit(organizationId, actorId, "CREATE", "ROOM", id);
        return room(organizationId, id);
    }

    @Transactional
    public RoomResponse updateRoom(long organizationId, long actorId, long id, RoomRequest request) {
        requireVersion(request.expectedVersion());
        validateRoomWing(organizationId, request.wingId());
        lock("timetable_rooms", organizationId, id, "Room");
        try {
            int changed = jdbc.sql("""
                    UPDATE timetable_rooms SET wing_id=:wing,code=:code,name=:name,capacity=:capacity,
                    exclusive_use=:exclusive,active=:active,version=version+1,updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("wing", request.wingId()).param("code", normalizeCode(request.code()))
                    .param("name", request.name().trim()).param("capacity", request.capacity())
                    .param("exclusive", request.exclusiveUse()).param("active", request.active())
                    .param("id", id).param("org", organizationId).param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Room", id, organizationId);
            replaceCapabilities(organizationId, id, request.capabilities());
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Room code or capability conflicts with existing configuration", ex);
        }
        audit(organizationId, actorId, "UPDATE", "ROOM", id);
        return room(organizationId, id);
    }

    @Transactional
    public long saveSubjectRoomRequirement(long organizationId, long actorId,
                                           SubjectRoomRequirementRequest request) {
        assertGradeSubject(organizationId, request.academicYearId(), request.gradeLevelId(), request.gradeLevelSubjectId());
        String capability = normalizeCode(request.capabilityCode());
        if (request.required()) jdbc.sql("""
                UPDATE timetable_subject_room_requirements SET required=FALSE,version=version+1
                WHERE organization_id=:org AND academic_year_id=:year AND grade_level_subject_id=:subject
                    AND capability_code<>:capability AND required
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("subject", request.gradeLevelSubjectId()).param("capability", capability).update();
        Long existing = jdbc.sql("""
                SELECT id FROM timetable_subject_room_requirements WHERE organization_id=:org
                AND academic_year_id=:year AND grade_level_subject_id=:subject AND capability_code=:capability
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("subject", request.gradeLevelSubjectId()).param("capability", capability)
                .query(Long.class).optional().orElse(null);
        long id;
        if (existing == null) {
            id = jdbc.sql("""
                    INSERT INTO timetable_subject_room_requirements
                    (organization_id,academic_year_id,grade_level_id,grade_level_subject_id,capability_code,required)
                    VALUES (:org,:year,:grade,:subject,:capability,:required) RETURNING id
                    """).param("org", organizationId).param("year", request.academicYearId())
                    .param("grade", request.gradeLevelId()).param("subject", request.gradeLevelSubjectId())
                    .param("capability", capability).param("required", request.required()).query(Long.class).single();
        } else {
            requireVersion(request.expectedVersion());
            int changed = jdbc.sql("""
                    UPDATE timetable_subject_room_requirements SET required=:required,version=version+1
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("required", request.required()).param("id", existing).param("org", organizationId)
                    .param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Subject room requirement", existing, organizationId);
            id = existing;
        }
        audit(organizationId, actorId, existing == null ? "CREATE" : "UPDATE", "SUBJECT_ROOM_REQUIREMENT", id);
        return id;
    }

    @Transactional
    public SectionHomeRoomResponse assignSectionHomeRoom(long organizationId, long actorId,
                                                         SectionHomeRoomRequest request) {
        assertSection(organizationId, request.academicYearId(), request.gradeLevelId(), request.sectionId());
        long eligibleRoom = jdbc.sql("""
                SELECT COUNT(*) FROM timetable_rooms room
                LEFT JOIN timetable_grade_wings gw ON gw.organization_id=room.organization_id
                    AND gw.academic_year_id=:year AND gw.grade_level_id=:grade
                WHERE room.id=:room AND room.organization_id=:org AND room.active
                    AND (room.wing_id IS NULL OR room.wing_id=gw.wing_id)
                """).param("year", request.academicYearId()).param("grade", request.gradeLevelId())
                .param("room", request.roomId()).param("org", organizationId).query(Long.class).single();
        if (eligibleRoom == 0) throw new InvalidTimetableConfigurationException(
                "Choose an active classroom in the same school wing as this section");
        Long existing = jdbc.sql("""
                SELECT id FROM timetable_section_home_rooms WHERE organization_id=:org
                    AND academic_year_id=:year AND section_id=:section
                """).param("org", organizationId).param("year", request.academicYearId())
                .param("section", request.sectionId()).query(Long.class).optional().orElse(null);
        long id;
        if (existing == null) {
            id = jdbc.sql("""
                    INSERT INTO timetable_section_home_rooms
                        (organization_id,academic_year_id,section_id,room_id)
                    VALUES (:org,:year,:section,:room) RETURNING id
                    """).param("org", organizationId).param("year", request.academicYearId())
                    .param("section", request.sectionId()).param("room", request.roomId())
                    .query(Long.class).single();
        } else {
            requireVersion(request.expectedVersion());
            int changed = jdbc.sql("""
                    UPDATE timetable_section_home_rooms SET room_id=:room,version=version+1,updated_at=CURRENT_TIMESTAMP
                    WHERE id=:id AND organization_id=:org AND version=:version
                    """).param("room", request.roomId()).param("id", existing).param("org", organizationId)
                    .param("version", request.expectedVersion()).update();
            ensureChanged(changed, "Section regular classroom", existing, organizationId);
            id = existing;
        }
        audit(organizationId, actorId, existing == null ? "CREATE" : "UPDATE", "SECTION_HOME_ROOM", id);
        return jdbc.sql("""
                SELECT h.id,h.academic_year_id,s.grade_level_id,h.section_id,h.room_id,h.version
                FROM timetable_section_home_rooms h JOIN sections s ON s.id=h.section_id
                    AND s.academic_year_id=h.academic_year_id AND s.organization_id=h.organization_id
                WHERE h.id=:id AND h.organization_id=:org
                """).param("id", id).param("org", organizationId).query((rs,n) -> new SectionHomeRoomResponse(
                rs.getLong("id"),rs.getLong("academic_year_id"),rs.getLong("grade_level_id"),
                rs.getLong("section_id"),rs.getLong("room_id"),rs.getLong("version"))).single();
    }

    @Transactional(readOnly = true)
    public ResolvedSectionSchedule resolvedSchedule(long organizationId, long academicYearId, long sectionId) {
        SectionKey section = jdbc.sql("""
                SELECT grade_level_id FROM sections WHERE id=:section AND organization_id=:org AND academic_year_id=:year
                """).param("section", sectionId).param("org", organizationId).param("year", academicYearId)
                .query((rs, n) -> new SectionKey(rs.getLong("grade_level_id"))).optional()
                .orElseThrow(() -> new TimetableConfigurationNotFoundException("Section not found"));
        Long wingId = jdbc.sql("""
                SELECT wing_id FROM timetable_grade_wings WHERE organization_id=:org
                AND academic_year_id=:year AND grade_level_id=:grade
                """).param("org", organizationId).param("year", academicYearId).param("grade", section.gradeLevelId())
                .query(Long.class).optional().orElseThrow(() ->
                        new TimetableConfigurationNotFoundException("No wing is configured for the section's grade"));
        ResolvedAssignment assignment = jdbc.sql("""
                SELECT bell_schedule_id, grade_level_id FROM timetable_bell_schedule_assignments
                WHERE organization_id=:org AND academic_year_id=:year AND wing_id=:wing
                AND (grade_level_id=:grade OR grade_level_id IS NULL)
                ORDER BY grade_level_id NULLS LAST LIMIT 1
                """).param("org", organizationId).param("year", academicYearId).param("wing", wingId)
                .param("grade", section.gradeLevelId()).query((rs, n) -> new ResolvedAssignment(
                        rs.getLong("bell_schedule_id"), (Long) rs.getObject("grade_level_id"))).optional()
                .orElseThrow(() -> new TimetableConfigurationNotFoundException("No bell schedule is assigned to the section's grade or wing"));
        return new ResolvedSectionSchedule(organizationId, academicYearId, section.gradeLevelId(), sectionId,
                wingId, assignment.gradeLevelId() == null ? AssignmentScope.WING_DEFAULT : AssignmentScope.GRADE_OVERRIDE,
                schoolTimeZoneClient.getTimeZone(organizationId).getId(), bellSchedule(organizationId, assignment.scheduleId()));
    }

    @Transactional(readOnly = true)
    public FeasibilityReport feasibility(long organizationId, long academicYearId) {
        assertYear(organizationId, academicYearId);
        List<FeasibilityIssue> issues = new ArrayList<>();
        List<Long> sections = jdbc.sql("SELECT id FROM sections WHERE organization_id=:org AND academic_year_id=:year")
                .param("org", organizationId).param("year", academicYearId).query(Long.class).list();
        for (long sectionId : sections) {
            ResolvedSectionSchedule resolved;
            try {
                resolved = resolvedSchedule(organizationId, academicYearId, sectionId);
            } catch (TimetableConfigurationNotFoundException ex) {
                issues.add(issue("MISSING_RESOLVED_BELL_SCHEDULE", ex.getMessage(), sectionId, null, null, null));
                continue;
            }
            int capacity = resolved.bellSchedule().days().stream().flatMap(d -> d.intervals().stream())
                    .mapToInt(i -> i.intervalType() == IntervalType.TEACHING ? 1 : 0).sum();
            int demand = jdbc.sql("""
                    SELECT COALESCE(SUM(periods_per_week),0) FROM timetable_section_subject_requirements
                    WHERE organization_id=:org AND academic_year_id=:year AND section_id=:section
                    """).param("org", organizationId).param("year", academicYearId).param("section", sectionId)
                    .query(Integer.class).single();
            if (demand > capacity) issues.add(issue("SECTION_PERIOD_CAPACITY_EXCEEDED",
                    "Section requires " + demand + " teaching periods but its resolved schedule has " + capacity,
                    sectionId, null, null, null));
        }
        jdbc.sql("""
                SELECT r.section_id,r.grade_level_subject_id,r.periods_per_week,r.required_each_working_day,
                       r.maximum_periods_per_day,r.double_period_blocks_per_week
                FROM timetable_section_subject_requirements r
                WHERE r.organization_id=:org AND r.academic_year_id=:year
                """).param("org", organizationId).param("year", academicYearId).query((rs, n) -> {
                    long sectionId = rs.getLong("section_id"); long subjectId = rs.getLong("grade_level_subject_id");
                    try {
                        ResolvedSectionSchedule schedule = resolvedSchedule(organizationId, academicYearId, sectionId);
                        int workingDays = (int) schedule.bellSchedule().days().stream().filter(BellDayResponse::workingDay).count();
                        int teachingCapacity = schedule.bellSchedule().days().stream().flatMap(d -> d.intervals().stream())
                                .mapToInt(i -> i.intervalType() == IntervalType.TEACHING ? 1 : 0).sum();
                        long adjacentDays = schedule.bellSchedule().days().stream().filter(d -> adjacentTeachingPairs(d) > 0).count();
                        TimetableConfigurationRules.validateRequirement(rs.getInt("periods_per_week"),
                                rs.getBoolean("required_each_working_day"), rs.getInt("maximum_periods_per_day"),
                                rs.getInt("double_period_blocks_per_week"), workingDays, teachingCapacity, adjacentDays);
                    } catch (InvalidTimetableConfigurationException ex) {
                        issues.add(issue("SUBJECT_FREQUENCY_IMPOSSIBLE", ex.getMessage(), sectionId, subjectId, null, null));
                    } catch (TimetableConfigurationNotFoundException ignored) {
                        // Already reported once at section level.
                    }
                    long teacherCount = jdbc.sql("""
                            SELECT COUNT(*) FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year
                            AND section_id=:section AND grade_level_subject_id=:subject AND assignment_type='SUBJECT_TEACHER'
                            """).param("org", organizationId).param("year", academicYearId).param("section", sectionId)
                            .param("subject", subjectId).query(Long.class).single();
                    long alternateCount = jdbc.sql("""
                            SELECT COUNT(*) FROM timetable_alternate_teacher_eligibilities
                            WHERE organization_id=:org AND academic_year_id=:year AND grade_level_subject_id=:subject
                            AND active AND (section_id IS NULL OR section_id=:section)
                            AND (valid_from IS NULL OR valid_from <= CURRENT_DATE) AND (valid_to IS NULL OR valid_to >= CURRENT_DATE)
                            """).param("org", organizationId).param("year", academicYearId).param("subject", subjectId)
                            .param("section", sectionId).query(Long.class).single();
                    if (teacherCount + alternateCount == 0) issues.add(issue("NO_ELIGIBLE_TEACHER",
                            "No committed subject teacher or approved alternate is available", sectionId, subjectId, null, null));
                    return 0;
                }).list();
        jdbc.sql("""
                SELECT p.teacher_user_id,p.maximum_periods_per_week,COALESCE(SUM(r.periods_per_week),0) committed_load
                FROM timetable_teacher_policies p
                LEFT JOIN teacher_assignments a ON a.organization_id=p.organization_id
                    AND a.academic_year_id=p.academic_year_id AND a.teacher_user_id=p.teacher_user_id
                    AND a.assignment_type='SUBJECT_TEACHER'
                LEFT JOIN timetable_section_subject_requirements r ON r.organization_id=a.organization_id
                    AND r.academic_year_id=a.academic_year_id AND r.section_id=a.section_id
                    AND r.grade_level_subject_id=a.grade_level_subject_id
                WHERE p.organization_id=:org AND p.academic_year_id=:year
                GROUP BY p.teacher_user_id,p.maximum_periods_per_week
                HAVING COALESCE(SUM(r.periods_per_week),0) > p.maximum_periods_per_week
                """).param("org", organizationId).param("year", academicYearId).query((rs, n) -> {
                    issues.add(issue("TEACHER_WEEKLY_LOAD_EXCEEDED",
                            "Committed subject demand of " + rs.getInt("committed_load") +
                                    " exceeds teacher weekly limit of " + rs.getInt("maximum_periods_per_week"),
                            null, null, rs.getLong("teacher_user_id"), null));
                    return 0;
                }).list();
        jdbc.sql("""
                SELECT DISTINCT r.section_id
                FROM timetable_section_subject_requirements r
                WHERE r.organization_id=:org AND r.academic_year_id=:year
                    AND NOT EXISTS (SELECT 1 FROM timetable_section_home_rooms home
                        JOIN timetable_rooms room ON room.id=home.room_id AND room.organization_id=home.organization_id
                        WHERE home.organization_id=r.organization_id AND home.academic_year_id=r.academic_year_id
                            AND home.section_id=r.section_id AND room.active)
                """).param("org", organizationId).param("year", academicYearId).query((rs, n) -> {
                    issues.add(issue("MISSING_HOME_CLASSROOM", "Choose the regular classroom for this section",
                            rs.getLong("section_id"), null, null, null));
                    return 0;
                }).list();
        jdbc.sql("""
                SELECT DISTINCT rr.grade_level_subject_id,rr.capability_code
                FROM timetable_subject_room_requirements rr
                WHERE rr.organization_id=:org AND rr.academic_year_id=:year AND rr.required
                AND NOT EXISTS (SELECT 1 FROM timetable_room_capabilities c JOIN timetable_rooms room ON room.id=c.room_id
                    AND room.organization_id=c.organization_id WHERE c.organization_id=rr.organization_id
                    AND c.capability_code=rr.capability_code AND room.active)
                """).param("org", organizationId).param("year", academicYearId).query((rs, n) -> {
                    issues.add(issue("MISSING_ROOM_CAPABILITY", "No active room provides required capability " + rs.getString("capability_code"),
                            null, rs.getLong("grade_level_subject_id"), null, rs.getString("capability_code")));
                    return 0;
                }).list();
        boolean passed = issues.stream().noneMatch(i -> i.severity() == FeasibilitySeverity.ERROR);
        return new FeasibilityReport(organizationId, academicYearId, OffsetDateTime.now(), passed, false, List.copyOf(issues));
    }

    private void validateRequirementAgainstSchedule(long org, SubjectRequirementRequest r) {
        ResolvedSectionSchedule schedule = resolvedSchedule(org, r.academicYearId(), r.sectionId());
        int workingDays = (int) schedule.bellSchedule().days().stream().filter(BellDayResponse::workingDay).count();
        int capacity = schedule.bellSchedule().days().stream().flatMap(d -> d.intervals().stream())
                .mapToInt(i -> i.intervalType() == IntervalType.TEACHING ? 1 : 0).sum();
        long daysWithPair = schedule.bellSchedule().days().stream().filter(day -> adjacentTeachingPairs(day) > 0).count();
        TimetableConfigurationRules.validateRequirement(r.periodsPerWeek(), r.requiredEachWorkingDay(),
                r.maximumPeriodsPerDay(), r.doublePeriodBlocksPerWeek(), workingDays, capacity, daysWithPair);
    }

    private long adjacentTeachingPairs(BellDayResponse day) {
        List<BellIntervalResponse> teaching = day.intervals().stream().filter(i -> i.intervalType() == IntervalType.TEACHING).toList();
        long pairs = 0;
        for (int i = 1; i < teaching.size(); i++) if (teaching.get(i - 1).endTime().equals(teaching.get(i).startTime())) pairs++;
        return pairs;
    }

    private void validateDays(List<BellDayRequest> days) {
        TimetableConfigurationRules.validateDays(days);
    }

    private void replaceDays(long org, long scheduleId, List<BellDayRequest> days) {
        for (BellDayRequest day : days) {
            long dayId = jdbc.sql("""
                    INSERT INTO timetable_bell_schedule_days
                    (organization_id,bell_schedule_id,day_of_week,working_day,day_start_time,day_end_time)
                    VALUES (:org,:schedule,:day,:working,:start,:end) RETURNING id
                    """).param("org", org).param("schedule", scheduleId).param("day", day.dayOfWeek())
                    .param("working", day.workingDay()).param("start", day.dayStartTime()).param("end", day.dayEndTime())
                    .query(Long.class).single();
            for (BellIntervalRequest interval : day.intervals() == null ? List.<BellIntervalRequest>of() : day.intervals()) {
                jdbc.sql("""
                        INSERT INTO timetable_bell_intervals
                        (organization_id,bell_schedule_day_id,interval_type,name,start_time,end_time,display_order,
                         coverage_mode,assignee_requirement,schedulable)
                        VALUES (:org,:day,:type,:name,:start,:end,:ordering,:coverage,:assignee,:schedulable)
                        """).param("org", org).param("day", dayId).param("type", interval.intervalType().name())
                        .param("name", interval.name().trim()).param("start", interval.startTime()).param("end", interval.endTime())
                        .param("ordering", interval.displayOrder()).param("coverage", interval.coverageMode().name())
                        .param("assignee", interval.assigneeRequirement().name()).param("schedulable", interval.schedulable()).update();
            }
        }
    }

    private WingResponse wing(long org, long id) {
        return jdbc.sql("SELECT id,code,name,active,display_order,version FROM timetable_wings WHERE id=:id AND organization_id=:org")
                .param("id", id).param("org", org).query((rs, n) -> new WingResponse(rs.getLong("id"), rs.getString("code"),
                        rs.getString("name"), rs.getBoolean("active"), rs.getInt("display_order"), rs.getLong("version")))
                .optional().orElseThrow(() -> new TimetableConfigurationNotFoundException("Wing not found"));
    }

    private BellScheduleResponse bellSchedule(long org, long id) {
        BellHead head = jdbc.sql("SELECT name,description,active,version FROM timetable_bell_schedules WHERE id=:id AND organization_id=:org")
                .param("id", id).param("org", org).query((rs, n) -> new BellHead(rs.getString("name"), rs.getString("description"),
                        rs.getBoolean("active"), rs.getLong("version"))).optional()
                .orElseThrow(() -> new TimetableConfigurationNotFoundException("Bell schedule not found"));
        List<BellDayResponse> days = jdbc.sql("""
                SELECT id,day_of_week,working_day,day_start_time,day_end_time FROM timetable_bell_schedule_days
                WHERE organization_id=:org AND bell_schedule_id=:schedule ORDER BY day_of_week
                """).param("org", org).param("schedule", id).query((rs, n) -> {
                    long dayId = rs.getLong("id");
                    List<BellIntervalResponse> intervals = jdbc.sql("""
                            SELECT id,interval_type,name,start_time,end_time,display_order,coverage_mode,assignee_requirement,schedulable
                            FROM timetable_bell_intervals WHERE organization_id=:org AND bell_schedule_day_id=:day ORDER BY start_time,display_order
                            """).param("org", org).param("day", dayId).query((ir, x) -> new BellIntervalResponse(ir.getLong("id"),
                            IntervalType.valueOf(ir.getString("interval_type")), ir.getString("name"), ir.getObject("start_time", LocalTime.class),
                            ir.getObject("end_time", LocalTime.class), ir.getInt("display_order"), CoverageMode.valueOf(ir.getString("coverage_mode")),
                            AssigneeRequirement.valueOf(ir.getString("assignee_requirement")), ir.getBoolean("schedulable"))).list();
                    return new BellDayResponse(dayId, rs.getInt("day_of_week"), rs.getBoolean("working_day"),
                            toLocalTime(rs.getTime("day_start_time")), toLocalTime(rs.getTime("day_end_time")), intervals);
                }).list();
        return new BellScheduleResponse(id, head.name(), head.description(), head.active(), head.version(), days);
    }

    private ScheduleAssignmentResponse scheduleAssignment(long org, long id) {
        return jdbc.sql("""
                SELECT id,academic_year_id,wing_id,grade_level_id,bell_schedule_id,version
                FROM timetable_bell_schedule_assignments WHERE id=:id AND organization_id=:org
                """).param("id", id).param("org", org).query((rs, n) -> new ScheduleAssignmentResponse(rs.getLong("id"),
                        rs.getLong("academic_year_id"), rs.getLong("wing_id"), (Long) rs.getObject("grade_level_id"),
                        rs.getLong("bell_schedule_id"), rs.getLong("version"))).single();
    }

    private SubjectRequirementResponse requirement(long org, long id) {
        return jdbc.sql("SELECT * FROM timetable_section_subject_requirements WHERE id=:id AND organization_id=:org")
                .param("id", id).param("org", org).query((rs, n) -> new SubjectRequirementResponse(rs.getLong("id"),
                        rs.getLong("academic_year_id"), rs.getLong("grade_level_id"), rs.getLong("section_id"),
                        rs.getLong("grade_level_subject_id"), rs.getInt("periods_per_week"), rs.getBoolean("required_each_working_day"),
                        rs.getInt("maximum_periods_per_day"), rs.getInt("double_period_blocks_per_week"),
                        rs.getBoolean("allow_consecutive_periods"), rs.getLong("version"))).single();
    }

    private RoomResponse room(long org, long id) {
        Set<String> capabilities = new LinkedHashSet<>(jdbc.sql("""
                SELECT capability_code FROM timetable_room_capabilities WHERE organization_id=:org AND room_id=:room ORDER BY capability_code
                """).param("org", org).param("room", id).query(String.class).list());
        return jdbc.sql("SELECT * FROM timetable_rooms WHERE id=:id AND organization_id=:org")
                .param("id", id).param("org", org).query((rs, n) -> new RoomResponse(rs.getLong("id"),
                        (Long) rs.getObject("wing_id"), rs.getString("code"), rs.getString("name"),
                        (Integer) rs.getObject("capacity"), rs.getBoolean("exclusive_use"), rs.getBoolean("active"),
                        capabilities, rs.getLong("version"))).optional()
                .orElseThrow(() -> new TimetableConfigurationNotFoundException("Room not found"));
    }

    private void replaceCapabilities(long org, long roomId, Set<String> capabilities) {
        jdbc.sql("DELETE FROM timetable_room_capabilities WHERE organization_id=:org AND room_id=:room")
                .param("org", org).param("room", roomId).update();
        if (capabilities == null) return;
        for (String capability : capabilities) jdbc.sql("""
                INSERT INTO timetable_room_capabilities (organization_id,room_id,capability_code)
                VALUES (:org,:room,:capability)
                """).param("org", org).param("room", roomId).param("capability", normalizeCode(capability)).update();
    }

    private void validateEligibleTeacher(long org, long teacherId) {
        TimetableConfigurationRules.validateTeacherEligibility(org, teacherId,
                teacherEligibilityClient.check(org, teacherId));
    }

    private void assertEligibilitySectionWingAgreement(long org, long year, long grade, Long section, Long wing) {
        if (section == null || wing == null) return;
        long matches = jdbc.sql("""
                SELECT COUNT(*) FROM sections s
                JOIN timetable_grade_wings gw ON gw.organization_id=s.organization_id
                    AND gw.academic_year_id=s.academic_year_id AND gw.grade_level_id=s.grade_level_id
                WHERE s.id=:section AND s.organization_id=:org AND s.academic_year_id=:year
                    AND s.grade_level_id=:grade AND gw.wing_id=:wing
                """).param("section", section).param("org", org).param("year", year)
                .param("grade", grade).param("wing", wing).query(Long.class).single();
        if (matches == 0) {
            throw new InvalidTimetableConfigurationException(
                    "Alternate teacher eligibility section and wing must agree with the grade timetable wing");
        }
    }

    private void assertGradeWingChangeIsConsistent(long org, long year, long grade, long newWing) {
        long overrideConflicts = jdbc.sql("""
                SELECT COUNT(*) FROM timetable_bell_schedule_assignments
                WHERE organization_id=:org AND academic_year_id=:year AND grade_level_id=:grade AND wing_id<>:wing
                """).param("org", org).param("year", year).param("grade", grade).param("wing", newWing)
                .query(Long.class).single();
        long eligibilityConflicts = jdbc.sql("""
                SELECT COUNT(*) FROM timetable_alternate_teacher_eligibilities
                WHERE organization_id=:org AND academic_year_id=:year AND grade_level_id=:grade
                    AND section_id IS NOT NULL AND wing_id IS NOT NULL AND wing_id<>:wing
                """).param("org", org).param("year", year).param("grade", grade).param("wing", newWing)
                .query(Long.class).single();
        if (overrideConflicts + eligibilityConflicts > 0) {
            throw new TimetableConfigurationConflictException(
                    "Grade wing cannot change while overrides or section-scoped eligibility reference another wing");
        }
    }

    private void assertYear(long org, long year) { assertComposite("academic_years", "id", year, null, 0, org, "Academic year"); }
    private void assertGrade(long org, long year, long grade) { assertComposite("grade_levels", "id", grade, "academic_year_id", year, org, "Grade level"); }
    private void assertSection(long org, long year, long grade, long section) {
        long count = jdbc.sql("SELECT COUNT(*) FROM sections WHERE id=:id AND organization_id=:org AND academic_year_id=:year AND grade_level_id=:grade")
                .param("id", section).param("org", org).param("year", year).param("grade", grade).query(Long.class).single();
        if (count == 0) throw new TimetableConfigurationNotFoundException("Section not found");
    }
    private void assertGradeSubject(long org, long year, long grade, long subject) {
        long count = jdbc.sql("SELECT COUNT(*) FROM grade_level_subjects WHERE id=:id AND organization_id=:org AND academic_year_id=:year AND grade_level_id=:grade")
                .param("id", subject).param("org", org).param("year", year).param("grade", grade).query(Long.class).single();
        if (count == 0) throw new TimetableConfigurationNotFoundException("Grade subject mapping not found");
    }
    private void assertSectionSubject(long org, SubjectRequirementRequest r) {
        assertSection(org, r.academicYearId(), r.gradeLevelId(), r.sectionId());
        assertGradeSubject(org, r.academicYearId(), r.gradeLevelId(), r.gradeLevelSubjectId());
    }
    private void assertComposite(String table, String idColumn, long id, String parentColumn, long parentId, long org, String label) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + "=:id AND organization_id=:org" +
                (parentColumn == null ? "" : " AND " + parentColumn + "=:parent");
        JdbcClient.StatementSpec statement = jdbc.sql(sql).param("id", id).param("org", org);
        if (parentColumn != null) statement = statement.param("parent", parentId);
        if (statement.query(Long.class).single() == 0) throw new TimetableConfigurationNotFoundException(label + " not found");
    }
    private void assertExists(String table, long org, long id, String label) { assertComposite(table, "id", id, null, 0, org, label); }
    private void validateRoomWing(long org, Long wingId) { if (wingId != null) assertExists("timetable_wings", org, wingId, "Wing"); }
    private Long findPolicy(long org, long year, long teacher) { return jdbc.sql("SELECT id FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher")
            .param("org", org).param("year", year).param("teacher", teacher).query(Long.class).optional().orElse(null); }
    private void lock(String table, long org, long id, String label) {
        if (jdbc.sql("SELECT id FROM " + table + " WHERE id=:id AND organization_id=:org FOR UPDATE")
                .param("id", id).param("org", org).query(Long.class).optional().isEmpty())
            throw new TimetableConfigurationNotFoundException(label + " not found");
    }
    private void ensureChanged(int changed, String label, long id, long org) {
        if (changed == 1) return;
        assertExists(label.equals("Wing") ? "timetable_wings" : label.equals("Room") ? "timetable_rooms" :
                label.equals("Bell schedule") ? "timetable_bell_schedules" :
                label.equals("Grade-wing mapping") ? "timetable_grade_wings" :
                label.equals("Bell schedule assignment") ? "timetable_bell_schedule_assignments" :
                label.equals("Subject requirement") ? "timetable_section_subject_requirements" :
                label.equals("Teacher policy") ? "timetable_teacher_policies" :
                label.equals("Alternate teacher eligibility") ? "timetable_alternate_teacher_eligibilities" :
                "timetable_subject_room_requirements", org, id, label);
        throw new TimetableConfigurationConflictException(label + " was modified by another request; reload and retry");
    }
    private void requireVersion(Long version) { if (version == null) throw new InvalidTimetableConfigurationException("expectedVersion is required when updating existing configuration"); }
    private void audit(long org, long actor, String action, String type, long id) {
        jdbc.sql("INSERT INTO timetable_configuration_audit (organization_id,actor_user_id,action,entity_type,entity_id) VALUES (:org,:actor,:action,:type,:id)")
                .param("org", org).param("actor", actor).param("action", action).param("type", type).param("id", id).update();
    }
    private FeasibilityIssue issue(String code, String message, Long section, Long subject, Long teacher, String capability) {
        return new FeasibilityIssue(code, FeasibilitySeverity.ERROR, message, section, subject, teacher, capability);
    }
    private String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalTime toLocalTime(Time value) { return value == null ? null : value.toLocalTime(); }
    private TimetableConfigurationConflictException conflict(String message, Exception cause) {
        var result = new TimetableConfigurationConflictException(message); result.initCause(cause); return result;
    }
    private record SectionKey(long gradeLevelId) {}
    private record ResolvedAssignment(long scheduleId, Long gradeLevelId) {}
    private record BellHead(String name, String description, boolean active, long version) {}
}
