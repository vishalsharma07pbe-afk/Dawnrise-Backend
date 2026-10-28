package com.dawnrise.academic.dailycoverage.service;

import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.AbsenceType;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.AffectedPeriod;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ApprovedCoverage;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ArrangementType;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.AssignmentView;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.Candidate;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.CandidateChoiceRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.CorrectAbsenceRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.CoverageAlert;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.CoverageReason;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.DailyEntry;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.DailyTimetable;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.HistoryEvent;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ManualPreview;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.MutationResponse;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ProposalSummary;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ReasonedVersionRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.RecordAbsenceRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.VersionRequest;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse;
import com.dawnrise.academic.timetable.exception.InvalidTimetableConfigurationException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationConflictException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DailyCoverageService {
    private static final Duration IMMINENT = Duration.ofMinutes(45);
    private final JdbcClient jdbc;
    private final SchoolTimeZoneClient timeZones;
    private final IdentityTeacherEligibilityClient identities;
    private final IdentityGuardianAccessClient guardians;

    public DailyCoverageService(JdbcClient jdbc, SchoolTimeZoneClient timeZones,
                                IdentityTeacherEligibilityClient identities,
                                IdentityGuardianAccessClient guardians) {
        this.jdbc = jdbc;
        this.timeZones = timeZones;
        this.identities = identities;
        this.guardians = guardians;
    }

    @Transactional
    public MutationResponse recordAbsence(long org, long actor, RecordAbsenceRequest request) {
        validateRange(request.startTime(), request.endTime());
        long year = academicYear(org, request.date());
        TeachingEligibilityResponse teacher = identities.check(org, request.teacherUserId());
        if (teacher == null || !teacher.eligible() || !Objects.equals(teacher.organizationId(), org)) {
            throw new InvalidTimetableConfigurationException("The teacher is not active in this organization");
        }
        String fingerprint = fingerprint(request.toString());
        ExistingIdempotency existing = jdbc.sql("SELECT id,version,request_fingerprint FROM timetable_teacher_absences WHERE organization_id=:org AND idempotency_key=:key")
                .param("org", org).param("key", request.requestKey()).query((rs, n) ->
                        new ExistingIdempotency(rs.getLong(1), rs.getLong(2), rs.getString(3))).optional().orElse(null);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) throw conflict("The request key was already used for a different absence");
            return new MutationResponse(existing.id(), existing.version());
        }
        long id;
        try {
            id = jdbc.sql("""
                    INSERT INTO timetable_teacher_absences
                    (organization_id,academic_year_id,teacher_user_id,teacher_display_name,absence_date,
                     start_time,end_time,absence_type,reason,idempotency_key,request_fingerprint,recorded_by_user_id)
                    VALUES(:org,:year,:teacher,:name,:date,:start,:end,:type,:reason,:key,:fingerprint,:actor)
                    RETURNING id
                    """).param("org", org).param("year", year).param("teacher", request.teacherUserId())
                    .param("name", teacher.displayName().trim()).param("date", request.date())
                    .param("start", request.startTime()).param("end", request.endTime())
                    .param("type", request.type().name()).param("reason", request.reason().trim())
                    .param("key", request.requestKey()).param("fingerprint", fingerprint).param("actor", actor)
                    .query(Long.class).single();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("This absence overlaps an existing confirmed absence or duplicate request");
        }
        createCases(org, actor, id, year, request.teacherUserId(), request.date(), request.startTime(), request.endTime(), request.wingIds());
        invalidateExistingCover(org, actor, id, year, request.teacherUserId(), request.date(), request.startTime(), request.endTime());
        event(org, year, id, null, null, "ABSENCE_RECORDED", request.reason(), actor);
        return new MutationResponse(id, 0);
    }

    @Transactional
    public MutationResponse correctAbsence(long org, long actor, long absenceId, CorrectAbsenceRequest request) {
        validateRange(request.startTime(), request.endTime());
        AbsenceHead head = absence(org, absenceId, true);
        requireVersion(head.version(), request.expectedVersion(), "Absence");
        if (!"CONFIRMED".equals(head.status())) throw conflict("Only a confirmed absence can be corrected");
        if (started(org, head.date(), head.startTime() == null ? LocalTime.MIN : head.startTime()) && request.reason().isBlank()) {
            throw new InvalidTimetableConfigurationException("An elapsed absence correction requires a reason");
        }
        AbsenceType type = request.type() == null ? head.type() : request.type();
        int changed = jdbc.sql("""
                UPDATE timetable_teacher_absences SET absence_type=:type,start_time=:start,end_time=:end,
                    reason=:reason,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND organization_id=:org AND version=:version
                """).param("type", type.name()).param("start", request.startTime()).param("end", request.endTime())
                .param("reason", request.reason().trim()).param("id", absenceId).param("org", org)
                .param("version", request.expectedVersion()).update();
        if (changed != 1) throw conflict("Absence changed while it was being corrected");
        jdbc.sql("UPDATE timetable_daily_cover_cases SET status='CANCELLED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND absence_id=:absence AND status<>'COVERED'")
                .param("org", org).param("absence", absenceId).update();
        createCases(org, actor, absenceId, head.year(), head.teacher(), head.date(), request.startTime(), request.endTime(), List.of());
        event(org, head.year(), absenceId, null, null, "ABSENCE_CORRECTED", request.reason(), actor);
        return new MutationResponse(absenceId, request.expectedVersion() + 1);
    }

    @Transactional
    public void cancelAbsence(long org, long actor, long absenceId, ReasonedVersionRequest request) {
        AbsenceHead head = absence(org, absenceId, true);
        requireVersion(head.version(), request.expectedVersion(), "Absence");
        int changed = jdbc.sql("""
                UPDATE timetable_teacher_absences SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP,
                    version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND organization_id=:org AND version=:version AND status='CONFIRMED'
                """).param("id", absenceId).param("org", org).param("version", request.expectedVersion()).update();
        if (changed != 1) throw conflict("Absence is no longer confirmed or was changed by another request");
        jdbc.sql("UPDATE timetable_daily_cover_proposals p SET status='CANCELLED',version=version+1,updated_at=CURRENT_TIMESTAMP FROM timetable_daily_cover_cases c WHERE c.id=p.case_id AND c.organization_id=:org AND c.absence_id=:absence AND p.status='AWAITING_APPROVAL'")
                .param("org", org).param("absence", absenceId).update();
        jdbc.sql("UPDATE timetable_daily_cover_cases SET status='CANCELLED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND absence_id=:absence AND status<>'COVERED'")
                .param("org", org).param("absence", absenceId).update();
        event(org, head.year(), absenceId, null, null, "ABSENCE_CANCELLED", request.reason(), actor);
    }

    @Transactional(readOnly = true)
    public List<AffectedPeriod> cases(long org, long year, LocalDate date, Long wing, String status) {
        requireYear(org, year, date);
        List<Long> ids = jdbc.sql("""
                SELECT c.id FROM timetable_daily_cover_cases c
                WHERE c.organization_id=:org AND c.academic_year_id=:year AND c.cover_date=:date
                  AND (CAST(:wing AS BIGINT) IS NULL OR c.wing_id=:wing)
                  AND (CAST(:status AS VARCHAR) IS NULL OR c.status=:status)
                ORDER BY c.start_time,c.section_name,c.id
                """).param("org", org).param("year", year).param("date", date).param("wing", wing)
                .param("status", backendStatus(status)).query(Long.class).list();
        return ids.stream().map(id -> affectedPeriod(org, id)).toList();
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(long org, long caseId) {
        CaseHead item = coverCase(org, caseId, false);
        LinkedHashMap<Long, Integer> ranked = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT teacher_user_id,MIN(rank) FROM (
                  SELECT ta.teacher_user_id,1 AS rank FROM teacher_assignments ta
                    JOIN timetable_grade_wings gw ON gw.organization_id=ta.organization_id AND gw.academic_year_id=ta.academic_year_id AND gw.grade_level_id=ta.grade_level_id
                    WHERE ta.organization_id=:org AND ta.academic_year_id=:year AND ta.grade_level_subject_id=:subject AND gw.wing_id=:wing
                  UNION ALL SELECT ta.teacher_user_id,2 FROM teacher_assignments ta
                    WHERE ta.organization_id=:org AND ta.academic_year_id=:year AND ta.section_id=:section
                  UNION ALL SELECT e.teacher_user_id,3 FROM timetable_alternate_teacher_eligibilities e
                    WHERE e.organization_id=:org AND e.academic_year_id=:year AND e.grade_level_subject_id=:subject AND e.active
                      AND (e.valid_from IS NULL OR e.valid_from<=:date) AND (e.valid_to IS NULL OR e.valid_to>=:date)
                  UNION ALL SELECT r.teacher_user_id,4 FROM timetable_substitute_roster r
                    WHERE r.organization_id=:org AND r.academic_year_id=:year AND r.active AND r.valid_from<=:date
                      AND (r.valid_to IS NULL OR r.valid_to>=:date)
                ) pool GROUP BY teacher_user_id ORDER BY MIN(rank),teacher_user_id
                """).param("org", org).param("year", item.year()).param("subject", item.subject())
                .param("wing", item.wing()).param("section", item.section()).param("date", item.date())
                .query((rs, n) -> Map.entry(rs.getLong(1), rs.getInt(2))).list()
                .forEach(entry -> ranked.putIfAbsent(entry.getKey(), entry.getValue()));
        if (!ranked.containsKey(item.originalTeacher())) ranked.put(item.originalTeacher(), 1);
        Map<Long, TeachingEligibilityResponse> names = new LinkedHashMap<>();
        if (!ranked.isEmpty()) identities.checkBatch(org, List.copyOf(ranked.keySet())).results()
                .forEach(value -> names.put(value.userId(), value));
        List<Candidate> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : ranked.entrySet()) {
            long teacher = entry.getKey();
            TeachingEligibilityResponse identity = names.get(teacher);
            List<CoverageReason> reasons = new ArrayList<>();
            List<CoverageReason> exclusions = new ArrayList<>();
            if (identity == null || !identity.eligible() || !Objects.equals(identity.organizationId(), org))
                exclusions.add(reason("IDENTITY_INELIGIBLE", "Teacher is not active and teaching-eligible in this school."));
            if (teacher == item.originalTeacher())
                exclusions.add(reason("ABSENT_TEACHER", "The originally assigned teacher is absent for this period."));
            if (teacherUnavailable(org, teacher, item))
                exclusions.add(reason("TIME_CONFLICT", "Teacher is absent, protected, resting, or already assigned at this time."));
            TravelCheck travel = travelCheck(org, teacher, item, entry.getValue());
            if (!travel.eligible()) exclusions.add(reason("TRAVEL_TIME", travel.message()));
            if (!restEligible(org, teacher, item))
                exclusions.add(reason("REST_LIMIT", "This cover would consume the teacher's protected minimum rest interval."));
            int workload = workload(org, teacher, item);
            Integer maximum = jdbc.sql("SELECT maximum_periods_per_day FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher")
                    .param("org", org).param("year", item.year()).param("teacher", teacher).query(Integer.class).optional().orElse(null);
            if (maximum != null && workload >= maximum)
                exclusions.add(reason("WORKLOAD_LIMIT", "This cover would exceed the teacher's daily workload limit."));
            if (!weeklyWorkloadEligible(org, teacher, item))
                exclusions.add(reason("WEEKLY_WORKLOAD_LIMIT", "This cover would exceed the teacher's weekly workload limit."));
            if (!consecutiveEligible(org, teacher, item))
                exclusions.add(reason("CONSECUTIVE_LIMIT", "This cover would exceed the teacher's consecutive-period limit."));
            reasons.add(reason("RANK_" + entry.getValue(), rankReason(entry.getValue())));
            String name = identity == null ? "Teacher #" + teacher : identity.displayName();
            AssignmentView assignment = new AssignmentView(teacher, name, item.subject(), item.subjectName(),
                    item.room(), item.roomName(), item.activity(), ArrangementType.TEACHER_COVER);
            result.add(new Candidate("TEACHER:" + teacher, "TEACHER", entry.getValue(), exclusions.isEmpty(),
                    false, assignment, List.copyOf(reasons), List.copyOf(exclusions), travel.minutes(), workload));
        }
        result.addAll(activityCandidates(org, item));
        result.sort(Comparator.comparingInt(Candidate::rank).thenComparing(Candidate::candidateKey));
        boolean recommendedSet = false;
        List<Candidate> marked = new ArrayList<>();
        for (Candidate candidate : result) {
            boolean recommended = !recommendedSet && candidate.eligible();
            if (recommended) recommendedSet = true;
            marked.add(new Candidate(candidate.candidateKey(), candidate.kind(), candidate.rank(), candidate.eligible(),
                    recommended, candidate.assignment(), candidate.reasons(), candidate.exclusions(),
                    candidate.travelMinutes(), candidate.workloadPeriods()));
        }
        return List.copyOf(marked);
    }

    @Transactional(readOnly = true)
    public ManualPreview preview(long org, long caseId, CandidateChoiceRequest request) {
        CaseHead item = coverCase(org, caseId, false);
        requireVersion(item.version(), request.expectedVersion(), "Coverage case");
        Candidate candidate = candidate(org, caseId, request.candidateKey());
        return new ManualPreview(candidate.eligible(), candidate.assignment(), candidate.reasons(),
                candidate.exclusions(), item.version());
    }

    @Transactional
    public ProposalSummary manualProposal(long org, long actor, long caseId, CandidateChoiceRequest request) {
        return createProposal(org, actor, caseId, request, "MANUAL", false);
    }

    @Transactional
    public ProposalSummary autonomousProposal(long org, long actor, long caseId, VersionRequest request) {
        CaseHead item = coverCase(org, caseId, true);
        requireVersion(item.version(), request.expectedVersion(), "Coverage case");
        if (started(org, item.date(), item.start()))
            throw conflict("An elapsed period cannot receive a new autonomous proposal");
        Candidate best = candidates(org, caseId).stream().filter(Candidate::eligible).findFirst()
                .orElseThrow(() -> conflict("No eligible arrangement is currently available"));
        return createProposal(org, actor, caseId,
                new CandidateChoiceRequest(best.candidateKey(), item.version(), null, request.requestKey()),
                "AUTONOMOUS", false);
    }

    @Transactional
    public ProposalSummary approve(long org, long actor, long proposalId, VersionRequest request) {
        ProposalHead proposal = proposal(org, proposalId, true);
        if ("APPROVED".equals(proposal.status()) && request.requestKey().equals(proposal.decisionKey()))
            return proposalSummary(org, proposalId);
        requireVersion(proposal.version(), request.expectedVersion(), "Proposal");
        CaseHead item = coverCase(org, proposal.caseId(), true);
        advisoryLock(org, item.date());
        Candidate current = candidate(org, item.id(), proposal.candidateKey());
        if (!current.eligible()) throw conflict("The proposed arrangement is no longer eligible; choose another candidate");
        int changed = jdbc.sql("""
                UPDATE timetable_daily_cover_proposals SET status='APPROVED',approved_by_user_id=:actor,
                    approved_at=CURRENT_TIMESTAMP,decision_request_key=:requestKey,
                    version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND organization_id=:org AND version=:version AND status='AWAITING_APPROVAL'
                """).param("actor", actor).param("requestKey", request.requestKey()).param("id", proposalId).param("org", org)
                .param("version", request.expectedVersion()).update();
        if (changed != 1) throw conflict("Proposal changed before approval");
        jdbc.sql("UPDATE timetable_daily_cover_cases SET status='COVERED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org")
                .param("id", item.id()).param("org", org).update();
        event(org, item.year(), item.absence(), item.id(), proposalId, "PROPOSAL_APPROVED", "Approved daily cover", actor);
        outbox(org, item.id(), "COVER_CHANGED", "approved:" + proposalId, "{\"proposalId\":" + proposalId + "}");
        return proposalSummary(org, proposalId);
    }

    @Transactional
    public ProposalSummary reject(long org, long actor, long proposalId, ReasonedVersionRequest request) {
        ProposalHead proposal = proposal(org, proposalId, true);
        if ("REJECTED".equals(proposal.status()) && request.requestKey().equals(proposal.decisionKey()))
            return proposalSummary(org, proposalId);
        requireVersion(proposal.version(), request.expectedVersion(), "Proposal");
        CaseHead item = coverCase(org, proposal.caseId(), true);
        int changed = jdbc.sql("UPDATE timetable_daily_cover_proposals SET status='REJECTED',decision_request_key=:requestKey,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org AND version=:version AND status='AWAITING_APPROVAL'")
                .param("requestKey", request.requestKey()).param("id", proposalId).param("org", org).param("version", request.expectedVersion()).update();
        if (changed != 1) throw conflict("Proposal changed before rejection");
        jdbc.sql("UPDATE timetable_daily_cover_cases SET status='COVER_NEEDED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org")
                .param("id", item.id()).param("org", org).update();
        event(org, item.year(), item.absence(), item.id(), proposalId, "PROPOSAL_REJECTED", request.reason(), actor);
        return proposalSummary(org, proposalId);
    }

    @Transactional
    public ProposalSummary change(long org, long actor, long caseId, CandidateChoiceRequest request, boolean correction) {
        CaseHead item = coverCase(org, caseId, true);
        requireVersion(item.version(), request.expectedVersion(), "Coverage case");
        boolean elapsed = started(org, item.date(), item.start());
        if (correction && !elapsed)
            throw new InvalidTimetableConfigurationException("Only an elapsed period can use the correction workflow");
        if (!correction && elapsed)
            throw conflict("An elapsed period must use a reasoned correction instead of change");
        if (request.reason() == null || request.reason().isBlank())
            throw new InvalidTimetableConfigurationException("A reason is required");
        jdbc.sql("UPDATE timetable_daily_cover_proposals SET status=:status,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND case_id=:case AND status='APPROVED'")
                .param("status", correction ? "CORRECTED" : "SUPERSEDED").param("org", org).param("case", caseId).update();
        jdbc.sql("UPDATE timetable_daily_cover_cases SET status=:status,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org")
                .param("status", correction ? "CORRECTED" : "COVER_NEEDED").param("id", caseId).param("org", org).update();
        event(org, item.year(), item.absence(), caseId, null,
                correction ? "ELAPSED_COVER_CORRECTED" : "COVER_CHANGED", request.reason(), actor);
        CaseHead refreshed = coverCase(org, caseId, true);
        return createProposal(org, actor, caseId,
                new CandidateChoiceRequest(request.candidateKey(), refreshed.version(), request.reason(), request.requestKey()),
                "MANUAL", correction);
    }

    @Transactional
    public void cancelCover(long org, long actor, long caseId, ReasonedVersionRequest request) {
        CaseHead item = coverCase(org, caseId, true);
        requireVersion(item.version(), request.expectedVersion(), "Coverage case");
        if (started(org, item.date(), item.start()))
            throw conflict("An elapsed period cannot be cancelled; use a reasoned correction");
        jdbc.sql("UPDATE timetable_daily_cover_proposals SET status='CANCELLED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND case_id=:case AND status IN ('AWAITING_APPROVAL','APPROVED')")
                .param("org", org).param("case", caseId).update();
        int changed = jdbc.sql("UPDATE timetable_daily_cover_cases SET status='CANCELLED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org AND version=:version")
                .param("id", caseId).param("org", org).param("version", request.expectedVersion()).update();
        if (changed != 1) throw conflict("Coverage changed before cancellation");
        event(org, item.year(), item.absence(), caseId, null, "COVER_CANCELLED", request.reason(), actor);
    }

    @Transactional(readOnly = true)
    public List<CoverageAlert> alerts(long org, long year, LocalDate date) {
        return cases(org, year, date, null, null).stream()
                .filter(item -> item.status().equals("COVER_NEEDED") || item.status().equals("UNCOVERED"))
                .map(item -> {
                    boolean noArrangement = candidates(org, item.id()).stream().noneMatch(Candidate::eligible);
                    boolean critical = noArrangement || !item.urgency().equals("ROUTINE")
                            || item.absenceType() == AbsenceType.UNEXPECTED_ABSENCE;
                    return new CoverageAlert(item.id(), critical ? "CRITICAL" : "WARNING",
                        (critical ? "Class uncovered: " : "Cover needed for ")
                                + item.gradeName() + " · " + item.sectionName() + " at " + item.startTime(),
                        item.id(), item.deadlineAt());
                }).toList();
    }

    @Transactional(readOnly = true)
    public DailyTimetable daily(long org, long year, LocalDate requested, Long wing, Long grade,
                                Long section, Long teacher) {
        ZoneId zone = timeZones.getTimeZone(org);
        LocalDate date = requested == null ? LocalDate.now(zone) : requested;
        Publication publication = publication(org, year, date);
        String sql = """
                SELECT e.id,e.grade_level_id,e.grade_name,e.section_id,e.section_name,e.wing_id,e.wing_name,
                       e.bell_schedule_interval_id,e.day_of_week,e.start_time,e.end_time,e.interval_type,e.activity_name,
                       e.grade_level_subject_id,e.subject_name,e.teacher_user_id,e.teacher_display_name,e.room_id,e.room_name,
                       c.id,p.id,p.mode,p.arrangement_type,p.cover_teacher_user_id,p.cover_teacher_display_name,
                       p.activity_name,p.supervisor_user_id,p.supervisor_display_name,p.room_id,p.room_name,p.approved_at,p.reason
                FROM timetable_publication_entries e
                LEFT JOIN timetable_daily_cover_cases c ON c.organization_id=e.organization_id AND c.publication_entry_id=e.id
                     AND c.cover_date=:date
                LEFT JOIN timetable_daily_cover_proposals p ON p.organization_id=e.organization_id AND p.case_id=c.id AND p.status='APPROVED'
                WHERE e.organization_id=:org AND e.academic_year_id=:year AND e.publication_id=:publication
                  AND e.day_of_week=:day
                  AND (CAST(:wing AS BIGINT) IS NULL OR e.wing_id=:wing)
                  AND (CAST(:grade AS BIGINT) IS NULL OR e.grade_level_id=:grade)
                  AND (CAST(:section AS BIGINT) IS NULL OR e.section_id=:section)
                  AND (CAST(:teacher AS BIGINT) IS NULL OR e.teacher_user_id=:teacher
                       OR p.cover_teacher_user_id=:teacher OR p.supervisor_user_id=:teacher)
                ORDER BY e.start_time,e.grade_level_id,e.section_id
                """;
        List<DailyEntry> entries = jdbc.sql(sql).param("date", date).param("org", org).param("year", year)
                .param("publication", publication.id()).param("day", date.getDayOfWeek().getValue())
                .param("wing", wing).param("grade", grade).param("section", section).param("teacher", teacher)
                .query((rs, n) -> dailyEntry(rs)).list();
        return new DailyTimetable(publication.id(), year, publication.schoolName(), publication.yearName(),
                publication.from(), publication.to(), publication.publishedAt(), OffsetDateTime.now(zone), zone.getId(), entries);
    }

    @Transactional(readOnly = true)
    public DailyTimetable student(long org, long student, long year, LocalDate date) {
        LocalDate resolved = date == null ? LocalDate.now(timeZones.getTimeZone(org)) : date;
        long section = jdbc.sql("SELECT section_id FROM student_enrollments WHERE organization_id=:org AND academic_year_id=:year AND student_user_id=:student AND enrolled_on<=:date AND (ended_on IS NULL OR ended_on>=:date) ORDER BY id DESC LIMIT 1")
                .param("org", org).param("year", year).param("student", student).param("date", resolved)
                .query(Long.class).optional().orElseThrow(() -> notFound("Active student enrollment not found"));
        return daily(org, year, resolved, null, null, section, null);
    }

    @Transactional(readOnly = true)
    public DailyTimetable parent(long org, long guardian, long student, long year, LocalDate date) {
        if (!guardians.hasActiveRelationship(org, guardian, student)) throw notFound("Daily timetable not found");
        return student(org, student, year, date);
    }

    private ProposalSummary createProposal(long org, long actor, long caseId, CandidateChoiceRequest request,
                                           String mode, boolean correction) {
        if (request.requestKey() == null || request.requestKey().isBlank())
            throw new InvalidTimetableConfigurationException("A request key is required for a coverage proposal");
        ProposalHead replay = jdbc.sql("SELECT id,case_id,status,version,CASE WHEN arrangement_type='TEACHER_COVER' THEN 'TEACHER:'||cover_teacher_user_id ELSE 'ACTIVITY:'||activity_plan_id||':'||supervisor_user_id||':'||room_id END,decision_request_key FROM timetable_daily_cover_proposals WHERE organization_id=:org AND request_key=:key")
                .param("org", org).param("key", request.requestKey()).query((rs, n) -> new ProposalHead(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6)))
                .optional().orElse(null);
        if (replay != null) {
            if (replay.caseId() != caseId || !replay.candidateKey().equals(request.candidateKey()))
                throw conflict("The request key was already used for a different coverage proposal");
            return proposalSummary(org, replay.id());
        }
        CaseHead item = coverCase(org, caseId, true);
        requireVersion(item.version(), request.expectedVersion(), "Coverage case");
        if (!correction && started(org, item.date(), item.start()))
            throw conflict("An elapsed period cannot receive a new proposal; use a reasoned correction");
        Candidate selected = candidate(org, caseId, request.candidateKey());
        if (!selected.eligible()) throw conflict("The selected arrangement is no longer eligible");
        advisoryLock(org, item.date());
        AssignmentView assignment = selected.assignment();
        boolean activity = assignment.arrangementType() == ArrangementType.ALTERNATIVE_ACTIVITY;
        String[] activityKey = activity ? request.candidateKey().split(":") : new String[0];
        Long activityId = activity ? Long.valueOf(activityKey[1]) : null;
        Long supervisor = activity ? Long.valueOf(activityKey[2]) : null;
        String supervisorName = activity ? assignment.teacherDisplayName() : null;
        Long coverTeacher = activity ? null : assignment.teacherUserId();
        boolean exclusive = assignment.roomId() != null && jdbc.sql("SELECT exclusive_use FROM timetable_rooms WHERE id=:room AND organization_id=:org")
                .param("room", assignment.roomId()).param("org", org).query(Boolean.class).optional().orElse(false);
        long id;
        try {
            id = jdbc.sql("""
                    INSERT INTO timetable_daily_cover_proposals
                    (organization_id,academic_year_id,case_id,cover_date,section_id,start_time,end_time,mode,
                     arrangement_type,status,cover_teacher_user_id,cover_teacher_display_name,activity_plan_id,
                     activity_name,supervisor_user_id,supervisor_display_name,room_id,room_name,room_exclusive,
                     rank,reason,request_key,proposed_by_user_id)
                    VALUES(:org,:year,:case,:date,:section,:start,:end,:mode,:arrangement,'AWAITING_APPROVAL',
                     :teacher,:teacherName,:activity,:activityName,:supervisor,:supervisorName,:room,:roomName,
                     :exclusive,:rank,:reason,:requestKey,:actor) RETURNING id
                    """).param("org", org).param("year", item.year()).param("case", caseId).param("date", item.date())
                    .param("section", item.section()).param("start", item.start()).param("end", item.end())
                    .param("mode", mode).param("arrangement", assignment.arrangementType().name())
                    .param("teacher", coverTeacher).param("teacherName", activity ? null : assignment.teacherDisplayName())
                    .param("activity", activityId).param("activityName", activity ? assignment.activityName() : null)
                    .param("supervisor", supervisor).param("supervisorName", supervisorName)
                    .param("room", assignment.roomId()).param("roomName", assignment.roomName()).param("exclusive", exclusive)
                    .param("rank", selected.rank()).param("reason", request.reason() == null || request.reason().isBlank()
                            ? selected.reasons().getFirst().message() : request.reason().trim())
                    .param("requestKey", request.requestKey()).param("actor", actor)
                    .query(Long.class).single();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("The teacher, section, or exclusive room was reserved by another coverage request");
        }
        jdbc.sql("UPDATE timetable_daily_cover_cases SET status='AWAITING_APPROVAL',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org AND version=:version")
                .param("id", caseId).param("org", org).param("version", item.version()).update();
        event(org, item.year(), item.absence(), caseId, id, "PROPOSAL_CREATED",
                correction ? "Reasoned correction awaiting approval" : "Coverage proposal awaiting approval", actor);
        outbox(org, caseId, "APPROVAL_REQUIRED", "proposal:" + id, "{\"proposalId\":" + id + "}");
        return proposalSummary(org, id);
    }

    private AffectedPeriod affectedPeriod(long org, long caseId) {
        CaseRow row = jdbc.sql("""
                SELECT c.id,c.version,c.cover_date,c.publication_id,c.publication_entry_id,e.bell_schedule_interval_id,
                       c.start_time,c.end_time,c.wing_id,c.wing_name,e.grade_level_id,e.grade_name,c.section_id,c.section_name,
                       c.original_grade_level_subject_id,c.original_subject_name,c.original_teacher_user_id,
                       c.original_teacher_display_name,c.original_room_id,c.original_room_name,c.original_activity_name,
                       c.status,c.absence_id,a.absence_type,a.reason
                FROM timetable_daily_cover_cases c
                JOIN timetable_publication_entries e ON e.id=c.publication_entry_id AND e.organization_id=c.organization_id
                JOIN timetable_teacher_absences a ON a.id=c.absence_id AND a.organization_id=c.organization_id
                WHERE c.id=:id AND c.organization_id=:org
                """).param("id", caseId).param("org", org).query((rs, n) -> new CaseRow(
                        rs.getLong(1), rs.getLong(2), rs.getObject(3, LocalDate.class), rs.getLong(4), rs.getLong(5),
                        rs.getLong(6), rs.getObject(7, LocalTime.class), rs.getObject(8, LocalTime.class), rs.getLong(9),
                        rs.getString(10), rs.getLong(11), rs.getString(12), rs.getLong(13), rs.getString(14),
                        (Long) rs.getObject(15), rs.getString(16), rs.getLong(17), rs.getString(18),
                        (Long) rs.getObject(19), rs.getString(20), rs.getString(21), rs.getString(22), rs.getLong(23),
                        AbsenceType.valueOf(rs.getString(24)), rs.getString(25))).optional()
                .orElseThrow(() -> notFound("Coverage case not found"));
        AssignmentView original = new AssignmentView(row.originalTeacher(), row.originalTeacherName(), row.subject(),
                row.subjectName(), row.room(), row.roomName(), row.activity(), ArrangementType.ORIGINAL);
        ProposalSummary active = jdbc.sql("SELECT id FROM timetable_daily_cover_proposals WHERE organization_id=:org AND case_id=:case AND status IN ('AWAITING_APPROVAL','APPROVED') ORDER BY id DESC LIMIT 1")
                .param("org", org).param("case", caseId).query(Long.class).optional().map(id -> proposalSummary(org, id)).orElse(null);
        String status = uiStatus(org, row.status(), row.date(), row.start());
        ZonedDateTime start = ZonedDateTime.of(row.date(), row.start(), timeZones.getTimeZone(org));
        String urgency = urgency(org, row.date(), row.start());
        return new AffectedPeriod(row.id(), row.version(), row.date(), row.publication(), row.entry(), row.interval(),
                row.start(), row.end(), row.wing(), row.wingName(), row.grade(), row.gradeName(), row.section(),
                row.sectionName(), original, status, urgency, start.toOffsetDateTime(), row.absence(), row.absenceType(),
                row.absenceReason(), active, history(org, caseId));
    }

    private List<HistoryEvent> history(long org, long caseId) {
        return jdbc.sql("SELECT id,event_type,COALESCE(reason,event_type),reason,actor_user_id,occurred_at FROM timetable_daily_cover_events WHERE organization_id=:org AND case_id=:case ORDER BY occurred_at,id")
                .param("org", org).param("case", caseId).query((rs, n) -> new HistoryEvent(rs.getLong(1),
                        rs.getString(2), rs.getString(3), rs.getString(4), "User #" + rs.getLong(5),
                        rs.getObject(6, OffsetDateTime.class))).list();
    }

    private ProposalSummary proposalSummary(long org, long proposalId) {
        return jdbc.sql("""
                SELECT id,version,mode,status,arrangement_type,cover_teacher_user_id,cover_teacher_display_name,
                       activity_plan_id,activity_name,supervisor_user_id,supervisor_display_name,room_id,room_name,
                       created_at FROM timetable_daily_cover_proposals WHERE id=:id AND organization_id=:org
                """).param("id", proposalId).param("org", org).query((rs, n) -> {
                    String arrangement = rs.getString(5);
                    boolean activity = "ALTERNATIVE_ACTIVITY".equals(arrangement);
                    Long teacher = activity ? (Long) rs.getObject(10) : (Long) rs.getObject(6);
                    String name = activity ? rs.getString(11) : rs.getString(7);
                    Long activityId = (Long) rs.getObject(8);
                    String key = activity ? "ACTIVITY:" + activityId + ":" + teacher + ":" + rs.getObject(12)
                            : "TEACHER:" + teacher;
                    AssignmentView assignment = new AssignmentView(teacher, name, null, null,
                            (Long) rs.getObject(12), rs.getString(13), activity ? rs.getString(9) : null,
                            ArrangementType.valueOf(arrangement));
                    return new ProposalSummary(rs.getLong(1), rs.getLong(2), rs.getString(3),
                            "AWAITING_APPROVAL".equals(rs.getString(4)) ? "PENDING_APPROVAL" : rs.getString(4),
                            key, assignment, rs.getObject(14, OffsetDateTime.class));
                }).optional().orElseThrow(() -> notFound("Coverage proposal not found"));
    }

    private DailyEntry dailyEntry(java.sql.ResultSet rs) throws java.sql.SQLException {
        AssignmentView original = new AssignmentView((Long) rs.getObject(16), rs.getString(17),
                (Long) rs.getObject(14), rs.getString(15), (Long) rs.getObject(18), rs.getString(19),
                rs.getString(13), ArrangementType.ORIGINAL);
        Long proposalId = (Long) rs.getObject(21);
        AssignmentView actual = original;
        ApprovedCoverage coverage = null;
        if (proposalId != null) {
            ArrangementType type = ArrangementType.valueOf(rs.getString(23));
            boolean activity = type == ArrangementType.ALTERNATIVE_ACTIVITY;
            actual = new AssignmentView(activity ? (Long) rs.getObject(27) : (Long) rs.getObject(24),
                    activity ? rs.getString(28) : rs.getString(25), activity ? null : (Long) rs.getObject(14),
                    activity ? null : rs.getString(15), (Long) rs.getObject(29), rs.getString(30),
                    activity ? rs.getString(26) : rs.getString(13), type);
            coverage = new ApprovedCoverage(rs.getLong(20), proposalId, "APPROVED", rs.getString(22),
                    rs.getObject(31, OffsetDateTime.class), rs.getString(32));
        }
        return new DailyEntry(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5),
                rs.getLong(6), rs.getString(7), rs.getLong(8), rs.getInt(9), rs.getObject(10, LocalTime.class),
                rs.getObject(11, LocalTime.class), rs.getString(12), actual.activityName(), actual.gradeLevelSubjectId(),
                actual.subjectName(), actual.teacherUserId(), actual.teacherDisplayName(), actual.roomId(), actual.roomName(),
                original, actual, coverage);
    }

    private void createCases(long org, long actor, long absence, long year, long teacher, LocalDate date,
                             LocalTime start, LocalTime end, List<Long> wingIds) {
        Publication publication = publication(org, year, date);
        List<Long> entries = jdbc.sql("""
                SELECT id FROM timetable_publication_entries
                WHERE organization_id=:org AND academic_year_id=:year AND publication_id=:publication
                  AND teacher_user_id=:teacher AND day_of_week=:day
                  AND (CAST(:start AS TIME) IS NULL OR end_time>:start)
                  AND (CAST(:end AS TIME) IS NULL OR start_time<:end)
                ORDER BY start_time,section_id
                """).param("org", org).param("year", year).param("publication", publication.id())
                .param("teacher", teacher).param("day", date.getDayOfWeek().getValue())
                .param("start", start).param("end", end).query(Long.class).list();
        for (Long entry : entries) {
            if (wingIds != null && !wingIds.isEmpty()) {
                Long entryWing = jdbc.sql("SELECT wing_id FROM timetable_publication_entries WHERE id=:id AND organization_id=:org")
                        .param("id", entry).param("org", org).query(Long.class).single();
                if (!wingIds.contains(entryWing)) continue;
            }
            Long caseId = jdbc.sql("""
                    INSERT INTO timetable_daily_cover_cases
                    (organization_id,academic_year_id,cover_date,publication_id,publication_entry_id,absence_id,
                     section_id,section_name,wing_id,wing_name,start_time,end_time,interval_type,original_activity_name,
                     original_grade_level_subject_id,original_subject_name,original_teacher_user_id,
                     original_teacher_display_name,original_room_id,original_room_name,status)
                    SELECT organization_id,academic_year_id,:date,publication_id,id,:absence,section_id,section_name,
                     wing_id,wing_name,start_time,end_time,interval_type,activity_name,grade_level_subject_id,subject_name,
                     teacher_user_id,teacher_display_name,room_id,room_name,'COVER_NEEDED'
                    FROM timetable_publication_entries WHERE id=:entry AND organization_id=:org
                    ON CONFLICT(organization_id,cover_date,publication_entry_id) DO UPDATE
                    SET absence_id=EXCLUDED.absence_id,
                        status=CASE WHEN timetable_daily_cover_cases.status='COVERED' THEN 'CORRECTED' ELSE 'COVER_NEEDED' END,
                        version=timetable_daily_cover_cases.version+1,
                        updated_at=CURRENT_TIMESTAMP
                    RETURNING id
                    """).param("date", date).param("absence", absence).param("entry", entry).param("org", org)
                    .query(Long.class).optional().orElse(null);
            if (caseId != null) {
                event(org, year, absence, caseId, null, "CASE_CREATED", "Cover needed", actor);
                outbox(org, caseId, "COVER_NEEDED", "cover-needed:" + caseId, "{\"caseId\":" + caseId + "}");
            }
        }
    }

    private void invalidateExistingCover(long org, long actor, long absence, long year, long teacher,
                                         LocalDate date, LocalTime start, LocalTime end) {
        List<Long> cases = jdbc.sql("""
                SELECT DISTINCT case_id FROM timetable_daily_cover_proposals
                WHERE organization_id=:org AND cover_date=:date AND resource_teacher_user_id=:teacher
                  AND status IN ('AWAITING_APPROVAL','APPROVED')
                  AND (CAST(:start AS TIME) IS NULL OR end_time>:start)
                  AND (CAST(:end AS TIME) IS NULL OR start_time<:end)
                ORDER BY case_id
                """).param("org", org).param("date", date).param("teacher", teacher)
                .param("start", start).param("end", end).query(Long.class).list();
        for (Long caseId : cases) {
            jdbc.sql("UPDATE timetable_daily_cover_proposals SET status=CASE WHEN status='APPROVED' THEN 'SUPERSEDED' ELSE 'CANCELLED' END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND case_id=:case AND status IN ('AWAITING_APPROVAL','APPROVED')")
                    .param("org", org).param("case", caseId).update();
            jdbc.sql("UPDATE timetable_daily_cover_cases SET status='COVER_NEEDED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:case AND organization_id=:org")
                    .param("case", caseId).param("org", org).update();
            event(org, year, absence, caseId, null, "COVER_CHANGED",
                    "Previously selected cover teacher became unavailable", actor);
            outbox(org, caseId, "COVER_CHANGED", "cover-unavailable:" + absence + ":" + caseId,
                    "{\"caseId\":" + caseId + ",\"reason\":\"COVER_TEACHER_UNAVAILABLE\"}");
        }
    }

    private List<Candidate> activityCandidates(long org, CaseHead item) {
        return jdbc.sql("""
                SELECT p.id,p.name,s.supervisor_user_id,r.id,r.name
                FROM timetable_cover_activity_plans p
                JOIN timetable_cover_activity_supervisors s ON s.activity_plan_id=p.id AND s.organization_id=p.organization_id AND s.active
                JOIN timetable_rooms r ON r.organization_id=p.organization_id AND r.active
                  AND (p.wing_id IS NULL OR r.wing_id=p.wing_id)
                WHERE p.organization_id=:org AND p.academic_year_id=:year AND p.active
                  AND (p.wing_id IS NULL OR p.wing_id=:wing)
                  AND (p.required_capability_code IS NULL OR EXISTS
                       (SELECT 1 FROM timetable_room_capabilities rc WHERE rc.organization_id=:org AND rc.room_id=r.id AND rc.capability_code=p.required_capability_code))
                ORDER BY p.id,s.supervisor_user_id,r.id
                """).param("org", org).param("year", item.year()).param("wing", item.wing())
                .query((rs, n) -> {
                    long plan = rs.getLong(1), supervisor = rs.getLong(3), room = rs.getLong(4);
                    TeachingEligibilityResponse identity = identities.check(org, supervisor);
                    List<CoverageReason> exclusions = new ArrayList<>();
                    if (identity == null || !identity.eligible() || teacherUnavailable(org, supervisor, item))
                        exclusions.add(reason("SUPERVISOR_UNAVAILABLE", "The approved activity supervisor is unavailable."));
                    if (roomUnavailable(org, room, item))
                        exclusions.add(reason("ROOM_UNAVAILABLE", "The required exclusive room is already in use."));
                    AssignmentView assignment = new AssignmentView(supervisor,
                            identity == null ? "Supervisor #" + supervisor : identity.displayName(), null, null,
                            room, rs.getString(5), rs.getString(2), ArrangementType.ALTERNATIVE_ACTIVITY);
                    return new Candidate("ACTIVITY:" + plan + ":" + supervisor + ":" + room,
                            "ALTERNATIVE_ACTIVITY", 5, exclusions.isEmpty(), false, assignment,
                            List.of(reason("APPROVED_ACTIVITY", "Approved staffed activity with a suitable room.")),
                            List.copyOf(exclusions), null, workload(org, supervisor, item));
                }).list();
    }

    private boolean roomUnavailable(long org, long room, CaseHead item) {
        boolean exclusive = jdbc.sql("SELECT exclusive_use FROM timetable_rooms WHERE id=:room AND organization_id=:org")
                .param("room", room).param("org", org).query(Boolean.class).optional().orElse(true);
        if (!exclusive) return false;
        if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM timetable_publication_entries WHERE organization_id=:org AND publication_id=:publication AND room_id=:room AND section_id<>:section AND day_of_week=:day AND start_time<:end AND end_time>:start)")
                .param("org", org).param("publication", item.publication()).param("room", room)
                .param("section", item.section()).param("day", item.date().getDayOfWeek().getValue())
                .param("start", item.start()).param("end", item.end()).query(Boolean.class).single()) return true;
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM timetable_daily_cover_proposals WHERE organization_id=:org AND cover_date=:date AND case_id<>:case AND room_id=:room AND room_exclusive AND status IN ('AWAITING_APPROVAL','APPROVED') AND start_time<:end AND end_time>:start)")
                .param("org", org).param("date", item.date()).param("case", item.id()).param("room", room)
                .param("start", item.start()).param("end", item.end()).query(Boolean.class).single();
    }

    private boolean teacherUnavailable(long org, long teacher, CaseHead item) {
        if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM timetable_teacher_absences WHERE organization_id=:org AND teacher_user_id=:teacher AND absence_date=:date AND status='CONFIRMED' AND int4range(start_minute,end_minute,'[)') && int4range(:start,:end,'[)'))")
                .param("org", org).param("teacher", teacher).param("date", item.date())
                .param("start", minute(item.start())).param("end", minute(item.end())).query(Boolean.class).single()) return true;
        if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher AND day_of_week=:day AND availability_type='UNAVAILABLE' AND start_time<:end AND end_time>:start)")
                .param("org", org).param("year", item.year()).param("teacher", teacher)
                .param("day", item.date().getDayOfWeek().getValue()).param("start", item.start()).param("end", item.end())
                .query(Boolean.class).single()) return true;
        if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM timetable_publication_entries WHERE organization_id=:org AND publication_id=:publication AND teacher_user_id=:teacher AND day_of_week=:day AND start_time<:end AND end_time>:start)")
                .param("org", org).param("publication", item.publication()).param("teacher", teacher)
                .param("day", item.date().getDayOfWeek().getValue()).param("start", item.start()).param("end", item.end())
                .query(Boolean.class).single()) return true;
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM timetable_daily_cover_proposals WHERE organization_id=:org AND cover_date=:date AND case_id<>:case AND resource_teacher_user_id=:teacher AND status IN ('AWAITING_APPROVAL','APPROVED') AND start_time<:end AND end_time>:start)")
                .param("org", org).param("date", item.date()).param("teacher", teacher)
                .param("case", item.id()).param("start", item.start()).param("end", item.end()).query(Boolean.class).single();
    }

    private int workload(long org, long teacher, CaseHead item) {
        long regular = jdbc.sql("SELECT COUNT(*) FROM timetable_publication_entries WHERE organization_id=:org AND publication_id=:publication AND teacher_user_id=:teacher AND day_of_week=:day AND interval_type='TEACHING'")
                .param("org", org).param("publication", item.publication()).param("teacher", teacher)
                .param("day", item.date().getDayOfWeek().getValue()).query(Long.class).single();
        long cover = jdbc.sql("SELECT COUNT(*) FROM timetable_daily_cover_proposals WHERE organization_id=:org AND cover_date=:date AND case_id<>:case AND resource_teacher_user_id=:teacher AND status IN ('AWAITING_APPROVAL','APPROVED')")
                .param("org", org).param("date", item.date()).param("case", item.id()).param("teacher", teacher)
                .query(Long.class).single();
        return Math.toIntExact(regular + cover);
    }

    private TravelCheck travelCheck(long org, long teacher, CaseHead item, int rank) {
        if (rank != 3) return new TravelCheck(true, null, "No cross-wing travel is required.");
        List<AdjacentAssignment> neighbors = jdbc.sql("""
                SELECT wing_id,start_time,end_time FROM timetable_publication_entries
                WHERE organization_id=:org AND publication_id=:publication AND teacher_user_id=:teacher
                  AND day_of_week=:day AND (end_time<=:start OR start_time>=:end)
                ORDER BY CASE WHEN end_time<=:start THEN :start-end_time ELSE start_time-:end END
                LIMIT 2
                """).param("org", org).param("publication", item.publication()).param("teacher", teacher)
                .param("day", item.date().getDayOfWeek().getValue()).param("start", item.start()).param("end", item.end())
                .query((rs, n) -> new AdjacentAssignment(rs.getLong(1), rs.getObject(2, LocalTime.class),
                        rs.getObject(3, LocalTime.class))).list();
        int requiredMaximum = 0;
        for (AdjacentAssignment neighbor : neighbors) {
            if (neighbor.wing() == item.wing()) continue;
            Integer required = jdbc.sql("SELECT minimum_minutes FROM timetable_wing_travel_times WHERE organization_id=:org AND academic_year_id=:year AND from_wing_id=:fromWing AND to_wing_id=:toWing")
                    .param("org", org).param("year", item.year()).param("fromWing", neighbor.wing())
                    .param("toWing", item.wing()).query(Integer.class).optional().orElse(null);
            if (required == null) return new TravelCheck(false, null,
                    "Cross-wing travel is not configured for an adjacent assignment.");
            long gap = neighbor.end().compareTo(item.start()) <= 0
                    ? Duration.between(neighbor.end(), item.start()).toMinutes()
                    : Duration.between(item.end(), neighbor.start()).toMinutes();
            if (gap < required) return new TravelCheck(false, required,
                    "There is not enough travel time from the adjacent wing assignment.");
            requiredMaximum = Math.max(requiredMaximum, required);
        }
        return new TravelCheck(true, requiredMaximum, "Configured cross-wing travel time is available.");
    }

    private boolean restEligible(long org, long teacher, CaseHead item) {
        Integer minimum = jdbc.sql("SELECT minimum_rest_minutes FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher")
                .param("org", org).param("year", item.year()).param("teacher", teacher)
                .query(Integer.class).optional().orElse(0);
        if (minimum == 0) return true;
        return !jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM timetable_publication_entries
                 WHERE organization_id=:org AND publication_id=:publication AND teacher_user_id=:teacher
                   AND day_of_week=:day AND ((end_time<=:start AND end_time>:before)
                     OR (start_time>=:end AND start_time<:after)))
                """).param("org", org).param("publication", item.publication()).param("teacher", teacher)
                .param("day", item.date().getDayOfWeek().getValue()).param("start", item.start())
                .param("end", item.end()).param("before", item.start().minusMinutes(minimum))
                .param("after", item.end().plusMinutes(minimum)).query(Boolean.class).single();
    }

    private boolean weeklyWorkloadEligible(long org, long teacher, CaseHead item) {
        Integer maximum = jdbc.sql("SELECT maximum_periods_per_week FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher")
                .param("org", org).param("year", item.year()).param("teacher", teacher)
                .query(Integer.class).optional().orElse(null);
        if (maximum == null) return true;
        long regular = jdbc.sql("SELECT COUNT(*) FROM timetable_publication_entries WHERE organization_id=:org AND publication_id=:publication AND teacher_user_id=:teacher AND interval_type='TEACHING'")
                .param("org", org).param("publication", item.publication()).param("teacher", teacher)
                .query(Long.class).single();
        LocalDate weekStart = item.date().minusDays(item.date().getDayOfWeek().getValue() - 1L);
        long cover = jdbc.sql("SELECT COUNT(*) FROM timetable_daily_cover_proposals WHERE organization_id=:org AND case_id<>:case AND resource_teacher_user_id=:teacher AND cover_date>=:from AND cover_date<:to AND status IN ('AWAITING_APPROVAL','APPROVED')")
                .param("org", org).param("case", item.id()).param("teacher", teacher)
                .param("from", weekStart).param("to", weekStart.plusDays(7))
                .query(Long.class).single();
        return regular + cover < maximum;
    }

    private boolean consecutiveEligible(long org, long teacher, CaseHead item) {
        Integer maximum = jdbc.sql("SELECT maximum_consecutive_periods FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher")
                .param("org", org).param("year", item.year()).param("teacher", teacher)
                .query(Integer.class).optional().orElse(null);
        if (maximum == null) return true;
        List<TimeRange> ranges = new ArrayList<>(jdbc.sql("SELECT start_time,end_time FROM timetable_publication_entries WHERE organization_id=:org AND publication_id=:publication AND teacher_user_id=:teacher AND day_of_week=:day AND interval_type='TEACHING' ORDER BY start_time")
                .param("org", org).param("publication", item.publication()).param("teacher", teacher)
                .param("day", item.date().getDayOfWeek().getValue())
                .query((rs, n) -> new TimeRange(rs.getObject(1, LocalTime.class), rs.getObject(2, LocalTime.class))).list());
        ranges.add(new TimeRange(item.start(), item.end()));
        ranges.sort(Comparator.comparing(TimeRange::start));
        int run = 0;
        LocalTime previousEnd = null;
        for (TimeRange range : ranges) {
            run = previousEnd != null && !range.start().isAfter(previousEnd) ? run + 1 : 1;
            if (run > maximum) return false;
            previousEnd = range.end();
        }
        return true;
    }

    private Candidate candidate(long org, long caseId, String key) {
        return candidates(org, caseId).stream().filter(value -> value.candidateKey().equals(key)).findFirst()
                .orElseThrow(() -> new InvalidTimetableConfigurationException("Choose a known coverage candidate"));
    }

    private CaseHead coverCase(long org, long id, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        return jdbc.sql("SELECT id,academic_year_id,cover_date,publication_id,absence_id,section_id,wing_id,start_time,end_time,original_grade_level_subject_id,original_subject_name,original_teacher_user_id,original_room_id,original_room_name,original_activity_name,version FROM timetable_daily_cover_cases WHERE id=:id AND organization_id=:org" + suffix)
                .param("id", id).param("org", org).query((rs, n) -> new CaseHead(rs.getLong(1), rs.getLong(2),
                        rs.getObject(3, LocalDate.class), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7),
                        rs.getObject(8, LocalTime.class), rs.getObject(9, LocalTime.class), (Long) rs.getObject(10),
                        rs.getString(11), rs.getLong(12), (Long) rs.getObject(13), rs.getString(14), rs.getString(15),
                        rs.getLong(16))).optional().orElseThrow(() -> notFound("Coverage case not found"));
    }

    private AbsenceHead absence(long org, long id, boolean lock) {
        return jdbc.sql("SELECT academic_year_id,teacher_user_id,absence_date,start_time,end_time,absence_type,status,version FROM timetable_teacher_absences WHERE id=:id AND organization_id=:org" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).param("org", org).query((rs, n) -> new AbsenceHead(rs.getLong(1), rs.getLong(2),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalTime.class), rs.getObject(5, LocalTime.class),
                        AbsenceType.valueOf(rs.getString(6)), rs.getString(7), rs.getLong(8))).optional()
                .orElseThrow(() -> notFound("Absence not found"));
    }

    private ProposalHead proposal(long org, long id, boolean lock) {
        return jdbc.sql("SELECT id,case_id,status,version,CASE WHEN arrangement_type='TEACHER_COVER' THEN 'TEACHER:'||cover_teacher_user_id ELSE 'ACTIVITY:'||activity_plan_id||':'||supervisor_user_id||':'||room_id END,decision_request_key FROM timetable_daily_cover_proposals WHERE id=:id AND organization_id=:org" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).param("org", org).query((rs, n) -> new ProposalHead(rs.getLong(1), rs.getLong(2),
                        rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6))).optional()
                .orElseThrow(() -> notFound("Coverage proposal not found"));
    }

    private Publication publication(long org, long year, LocalDate date) {
        return jdbc.sql("SELECT p.id,p.effective_from,p.effective_to_exclusive,p.published_at,y.name,p.school_name FROM timetable_publications p JOIN academic_years y ON y.id=p.academic_year_id AND y.organization_id=p.organization_id WHERE p.organization_id=:org AND p.academic_year_id=:year AND p.effective_from<=:date AND (p.effective_to_exclusive IS NULL OR p.effective_to_exclusive>:date) ORDER BY p.effective_from DESC LIMIT 1")
                .param("org", org).param("year", year).param("date", date).query((rs, n) -> new Publication(rs.getLong(1),
                        rs.getObject(2, LocalDate.class), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, OffsetDateTime.class), rs.getString(5), rs.getString(6))).optional()
                .orElseThrow(() -> notFound("Published timetable not found for the requested date"));
    }

    private long academicYear(long org, LocalDate date) {
        return jdbc.sql("SELECT id FROM academic_years WHERE organization_id=:org AND start_date<=:date AND end_date>=:date AND status<>'VOIDED' ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END,id LIMIT 1")
                .param("org", org).param("date", date).query(Long.class).optional()
                .orElseThrow(() -> notFound("Academic year not found for the absence date"));
    }

    private void requireYear(long org, long year, LocalDate date) {
        boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM academic_years WHERE id=:year AND organization_id=:org AND start_date<=:date AND end_date>=:date)")
                .param("year", year).param("org", org).param("date", date).query(Boolean.class).single();
        if (!exists) throw notFound("Academic year not found for the coverage date");
    }

    private void advisoryLock(long org, LocalDate date) {
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:org,:day)) locked")
                .param("org", Math.toIntExact(org)).param("day", (int) date.toEpochDay())
                .query(Integer.class).single();
    }

    private void event(long org, long year, Long absence, Long coverCase, Long proposal,
                       String type, String reason, long actor) {
        jdbc.sql("INSERT INTO timetable_daily_cover_events(organization_id,academic_year_id,absence_id,case_id,proposal_id,event_type,reason,actor_user_id) VALUES(:org,:year,:absence,:case,:proposal,:type,:reason,:actor)")
                .param("org", org).param("year", year).param("absence", absence).param("case", coverCase)
                .param("proposal", proposal).param("type", type).param("reason", reason).param("actor", actor).update();
    }

    private void outbox(long org, Long coverCase, String type, String key, String payload) {
        jdbc.sql("INSERT INTO timetable_daily_cover_notification_outbox(event_id,organization_id,case_id,event_type,idempotency_key,payload) VALUES(:event,:org,:case,:type,:key,CAST(:payload AS JSONB)) ON CONFLICT(organization_id,idempotency_key) DO NOTHING")
                .param("event", UUID.randomUUID()).param("org", org).param("case", coverCase).param("type", type).param("key", key)
                .param("payload", payload).update();
    }

    private String urgency(long org, LocalDate date, LocalTime start) {
        ZonedDateTime now = ZonedDateTime.now(timeZones.getTimeZone(org));
        ZonedDateTime begins = ZonedDateTime.of(date, start, now.getZone());
        if (!begins.isAfter(now)) return "IMMINENT";
        return Duration.between(now, begins).compareTo(IMMINENT) <= 0 ? "URGENT" : "ROUTINE";
    }

    private String uiStatus(long org, String backend, LocalDate date, LocalTime start) {
        if ("COVERED".equals(backend) && started(org, date, start)) return "ELAPSED";
        return switch (backend) {
            case "AWAITING_APPROVAL" -> "PROPOSED";
            case "COVERED" -> "APPROVED";
            case "CORRECTED" -> "ELAPSED";
            default -> backend;
        };
    }

    private boolean started(long org, LocalDate date, LocalTime time) {
        ZoneId zone = org == 0 ? ZoneId.systemDefault() : timeZones.getTimeZone(org);
        return !ZonedDateTime.of(date, time, zone).isAfter(ZonedDateTime.now(zone));
    }

    private String backendStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return switch (status) {
            case "PROPOSED" -> "AWAITING_APPROVAL";
            case "APPROVED", "ELAPSED" -> "COVERED";
            default -> status;
        };
    }

    private void validateRange(LocalTime start, LocalTime end) {
        if ((start == null) != (end == null) || start != null && !end.isAfter(start))
            throw new InvalidTimetableConfigurationException("Absence times must both be omitted or form a valid increasing range");
    }

    private void requireVersion(long actual, long expected, String label) {
        if (actual != expected) throw conflict(label + " changed while it was being reviewed; reload and retry");
    }

    private int minute(LocalTime value) { return value.getHour() * 60 + value.getMinute(); }
    private CoverageReason reason(String code, String message) { return new CoverageReason(code, message); }
    private String rankReason(int rank) { return switch (rank) {
        case 1 -> "Qualified for the same subject in the same wing.";
        case 2 -> "Already assigned to this section for another responsibility.";
        case 3 -> "Subject-qualified alternate teacher; availability and travel were checked.";
        case 4 -> "Available member of the approved substitute roster.";
        default -> "Approved staffed alternative activity.";
    }; }
    private TimetableConfigurationConflictException conflict(String message) { return new TimetableConfigurationConflictException(message); }
    private TimetableConfigurationNotFoundException notFound(String message) { return new TimetableConfigurationNotFoundException(message); }
    private String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private record ExistingIdempotency(long id, long version, String fingerprint) {}
    private record AbsenceHead(long year, long teacher, LocalDate date, LocalTime startTime,
                               LocalTime endTime, AbsenceType type, String status, long version) {}
    private record CaseHead(long id, long year, LocalDate date, long publication, long absence,
                            long section, long wing, LocalTime start, LocalTime end, Long subject,
                            String subjectName, long originalTeacher, Long room, String roomName,
                            String activity, long version) {}
    private record ProposalHead(long id, long caseId, String status, long version, String candidateKey, String decisionKey) {}
    private record AdjacentAssignment(long wing, LocalTime start, LocalTime end) {}
    private record TravelCheck(boolean eligible, Integer minutes, String message) {}
    private record TimeRange(LocalTime start, LocalTime end) {}
    private record Publication(long id, LocalDate from, LocalDate to, OffsetDateTime publishedAt,
                               String yearName, String schoolName) {}
    private record CaseRow(long id, long version, LocalDate date, long publication, long entry,
                           long interval, LocalTime start, LocalTime end, long wing, String wingName,
                           long grade, String gradeName, long section, String sectionName, Long subject,
                           String subjectName, long originalTeacher, String originalTeacherName, Long room,
                           String roomName, String activity, String status, long absence,
                           AbsenceType absenceType, String absenceReason) {}
}
