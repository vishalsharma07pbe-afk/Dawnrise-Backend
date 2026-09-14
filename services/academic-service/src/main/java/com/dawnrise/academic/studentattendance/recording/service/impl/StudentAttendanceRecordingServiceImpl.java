package com.dawnrise.academic.studentattendance.recording.service.impl;

import com.dawnrise.academic.academiccalendar.entity.AcademicCalendarDay;
import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.repository.AcademicCalendarDayRepository;
import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.section.exception.SectionNotFoundException;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendancePolicy;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendanceStatusPolicy;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendanceStatusPolicyRepository;
import com.dawnrise.academic.studentattendance.policy.service.StudentLatePenaltyCalculator;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncAppliedResult;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRecordRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineDraftRosterEntry;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineDraftSnapshot;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncMode;
import com.dawnrise.academic.studentattendance.recording.dto.BulkStudentAttendanceRecordRequest;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceRecordRequest;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceSessionNotFoundException;
import com.dawnrise.academic.studentattendance.recording.mapper.StudentAttendanceRecordingMapper;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceRecordRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationOutboxService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentAttendanceRecordingServiceImpl
        implements com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceRecordingService {

    private static final int MAX_BULK_RECORDS = 100;

    private final StudentAttendanceSessionRepository sessionRepository;
    private final StudentAttendanceRecordRepository recordRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SectionRepository sectionRepository;
    private final AcademicCalendarDayRepository calendarDayRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentAttendancePolicyRepository policyRepository;
    private final StudentAttendanceStatusPolicyRepository statusPolicyRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final StudentLatePenaltyCalculator latePenaltyCalculator;
    private final StudentAttendanceRecordingMapper mapper;
    private final SchoolTimeZoneClient schoolTimeZoneClient;
    private final StudentAttendanceNotificationOutboxService notificationOutboxService;
    private final Clock clock;

    public StudentAttendanceRecordingServiceImpl(
            StudentAttendanceSessionRepository sessionRepository,
            StudentAttendanceRecordRepository recordRepository,
            AcademicYearRepository academicYearRepository,
            SectionRepository sectionRepository,
            AcademicCalendarDayRepository calendarDayRepository,
            StudentEnrollmentRepository enrollmentRepository,
            StudentAttendancePolicyRepository policyRepository,
            StudentAttendanceStatusPolicyRepository statusPolicyRepository,
            TeacherAssignmentRepository teacherAssignmentRepository,
            StudentLatePenaltyCalculator latePenaltyCalculator,
            StudentAttendanceRecordingMapper mapper,
            SchoolTimeZoneClient schoolTimeZoneClient,
            StudentAttendanceNotificationOutboxService
                    notificationOutboxService,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.academicYearRepository = academicYearRepository;
        this.sectionRepository = sectionRepository;
        this.calendarDayRepository = calendarDayRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.policyRepository = policyRepository;
        this.statusPolicyRepository = statusPolicyRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.latePenaltyCalculator = latePenaltyCalculator;
        this.mapper = mapper;
        this.schoolTimeZoneClient = schoolTimeZoneClient;
        this.notificationOutboxService = notificationOutboxService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAttendanceOfflineDraftSnapshot previewOfflineDraft(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate
    ) {
        validateMutationContext(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                attendanceDate,
                actorUserId
        );
        Optional<StudentAttendanceSession> existingSession =
                sessionRepository.findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate(
                        organizationId,
                        academicYearId,
                        sectionId,
                        attendanceDate
                );
        existingSession.ifPresent(StudentAttendanceSession::requireDraft);
        List<StudentEnrollment> roster = enrollmentRepository.findEligibleForAttendanceDate(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                attendanceDate
        );
        Map<Long, Long> recordVersions = existingSession
                .map(session -> recordRepository
                        .findAllByAttendanceSessionIdOrderByIdAsc(session.getId())
                        .stream()
                        .collect(Collectors.toMap(
                                StudentAttendanceRecord::getStudentEnrollmentId,
                                StudentAttendanceRecord::getVersion
                        )))
                .orElseGet(Map::of);
        return new StudentAttendanceOfflineDraftSnapshot(
                existingSession.map(StudentAttendanceSession::getVersion).orElse(null),
                roster.stream()
                        .map(enrollment -> new StudentAttendanceOfflineDraftRosterEntry(
                                enrollment.getId(),
                                enrollment.getRollNumber(),
                                recordVersions.get(enrollment.getId())
                        ))
                        .toList()
        );
    }

    @Override
    @Transactional
    public StudentAttendanceOfflineSyncAppliedResult synchronizeOfflineDraft(
            long organizationId,
            long actorUserId,
            StudentAttendanceOfflineSyncRequest request
    ) {
        validateOfflineRequest(request);
        AttendanceValidationContext context = validateMutationContext(
                organizationId,
                request.academicYearId(),
                request.gradeLevelId(),
                request.sectionId(),
                request.attendanceDate(),
                actorUserId
        );

        Optional<StudentAttendanceSession> locked =
                sessionRepository.findByContextForUpdate(
                        organizationId,
                        request.academicYearId(),
                        request.sectionId(),
                        request.attendanceDate()
                );
        StudentAttendanceSession session;
        if (locked.isEmpty()) {
            if (request.baseSessionVersion() != null) {
                throw new StudentAttendanceRecordingConflictException(
                        "Attendance session no longer matches the offline base version"
                );
            }
            session = sessionRepository.saveAndFlush(
                    new StudentAttendanceSession(
                            organizationId,
                            request.academicYearId(),
                            request.gradeLevelId(),
                            request.sectionId(),
                            context.calendarDay().getId(),
                            request.attendanceDate(),
                            actorUserId
                    )
            );
        } else {
            session = locked.get();
            session.requireDraft();
            if (request.baseSessionVersion() == null
                    || !Objects.equals(
                    session.getVersion(),
                    request.baseSessionVersion()
            )) {
                throw new StudentAttendanceRecordingConflictException(
                        "Attendance session changed after the offline draft was created"
                );
            }
        }

        List<Long> requestedEnrollmentIds = request.records().stream()
                .map(StudentAttendanceOfflineSyncRecordRequest::studentEnrollmentId)
                .toList();
        Map<Long, StudentEnrollment> enrollments =
                enrollmentRepository.findEligibleForAttendanceDateByIds(
                                organizationId,
                                request.academicYearId(),
                                request.gradeLevelId(),
                                request.sectionId(),
                                request.attendanceDate(),
                                requestedEnrollmentIds
                        ).stream()
                        .collect(Collectors.toMap(
                                StudentEnrollment::getId,
                                Function.identity()
                        ));
        if (enrollments.size() != requestedEnrollmentIds.size()) {
            throw new InvalidStudentAttendanceRecordingException(
                    "All student enrollments must belong to the section and cover the attendance date"
            );
        }

        Set<Long> requiredIds = requiredEnrollments(session).stream()
                .map(StudentEnrollment::getId)
                .collect(Collectors.toSet());
        Set<Long> expectedRosterIds = new HashSet<>(request.expectedRosterEnrollmentIds());
        if (!requiredIds.equals(expectedRosterIds)) {
            throw new StudentAttendanceRecordingConflictException(
                    "The section roster changed after the offline draft was created"
            );
        }
        if (request.syncMode() == StudentAttendanceOfflineSyncMode.COMPLETE
                && !requiredIds.equals(new HashSet<>(requestedEnrollmentIds))) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Complete offline synchronization must include the entire section roster"
            );
        }

        Map<Long, StudentAttendanceRecord> existing =
                recordRepository.findAllByAttendanceSessionIdAndStudentEnrollmentIdIn(
                                session.getId(),
                                requestedEnrollmentIds
                        ).stream()
                        .collect(Collectors.toMap(
                                StudentAttendanceRecord::getStudentEnrollmentId,
                                Function.identity()
                        ));
        Map<AttendanceStatus, StudentAttendanceStatusPolicy> credits =
                statusCredits(organizationId);
        StudentAttendancePolicy policy = policy(organizationId);
        Map<Long, Integer> requestLateCounts = new HashMap<>();
        List<StudentAttendanceRecord> orderedApplied = new ArrayList<>();

        for (StudentAttendanceOfflineSyncRecordRequest item : request.records()) {
            StudentAttendanceRecord record = existing.get(item.studentEnrollmentId());
            if (record == null && item.expectedRecordVersion() != null) {
                throw new StudentAttendanceRecordingConflictException(
                        "An attendance record no longer matches its offline base version"
                );
            }
            if (record != null && (item.expectedRecordVersion() == null
                    || !Objects.equals(
                    record.getVersion(),
                    item.expectedRecordVersion()
            ))) {
                throw new StudentAttendanceRecordingConflictException(
                        "An attendance record changed after the offline draft was created"
                );
            }

            AttendanceStatus effective = effectiveStatus(
                    session,
                    policy,
                    item.studentEnrollmentId(),
                    item.recordedStatus(),
                    requestLateCounts
            );
            boolean penaltyApplied = item.recordedStatus() == AttendanceStatus.LATE
                    && effective != AttendanceStatus.LATE;
            StudentAttendanceStatusPolicy credit = credits.get(effective);
            StudentEnrollment enrollment = enrollments.get(item.studentEnrollmentId());
            if (record == null) {
                record = new StudentAttendanceRecord(
                        session,
                        enrollment.getId(),
                        enrollment.getStudentUserId(),
                        item.recordedStatus(),
                        effective,
                        credit.getEarnedCredit(),
                        credit.getPossibleCredit(),
                        penaltyApplied,
                        item.remarks(),
                        actorUserId
                );
            } else {
                record.replace(
                        item.recordedStatus(),
                        effective,
                        credit.getEarnedCredit(),
                        credit.getPossibleCredit(),
                        penaltyApplied,
                        item.remarks(),
                        actorUserId
                );
            }
            orderedApplied.add(record);
        }

        if (request.syncMode() == StudentAttendanceOfflineSyncMode.COMPLETE) {
            recordRepository.deleteByAttendanceSessionIdAndStudentEnrollmentIdNotIn(
                    session.getId(),
                    requestedEnrollmentIds
            );
        }
        recordRepository.saveAllAndFlush(orderedApplied);
        session.touch(actorUserId);
        sessionRepository.saveAndFlush(session);
        List<StudentAttendanceRecord> allRecords =
                recordRepository.findAllByAttendanceSessionIdOrderByIdAsc(session.getId());
        return new StudentAttendanceOfflineSyncAppliedResult(
                mapper.toResponse(session, allRecords),
                orderedApplied.stream().map(mapper::toResponse).toList()
        );
    }

    private void validateOfflineRequest(StudentAttendanceOfflineSyncRequest request) {
        if (request == null
                || request.records() == null
                || request.records().isEmpty()) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Offline attendance records are required"
            );
        }
        if (request.records().size() > MAX_BULK_RECORDS) {
            throw new InvalidStudentAttendanceRecordingException(
                    "At most 100 offline attendance records can be synchronized at once"
            );
        }
        if (request.syncMode() == null) {
            throw new InvalidStudentAttendanceRecordingException("Offline sync mode is required");
        }
        if (request.expectedRosterEnrollmentIds() == null
                || request.expectedRosterEnrollmentIds().isEmpty()) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Expected section roster is required"
            );
        }
        if (request.expectedRosterEnrollmentIds().size() > 500
                || request.expectedRosterEnrollmentIds().stream()
                .anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(request.expectedRosterEnrollmentIds()).size()
                != request.expectedRosterEnrollmentIds().size()) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Expected section roster must contain at most 500 unique positive enrollment IDs"
            );
        }
        Set<Long> ids = new HashSet<>();
        for (StudentAttendanceOfflineSyncRecordRequest item : request.records()) {
            if (item == null || item.studentEnrollmentId() == null
                    || item.studentEnrollmentId() <= 0
                    || item.recordedStatus() == null) {
                throw new InvalidStudentAttendanceRecordingException(
                        "Each offline attendance record is incomplete"
                );
            }
            if (!ids.add(item.studentEnrollmentId())) {
                throw new InvalidStudentAttendanceRecordingException(
                        "Duplicate student enrollment IDs are not allowed"
                );
            }
        }
    }

    @Override
    @Transactional
    public StudentAttendanceSessionResponse getOrCreateDraft(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate
    ) {
        AttendanceValidationContext context = validateMutationContext(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                attendanceDate,
                actorUserId
        );
        Optional<StudentAttendanceSession> existing =
                sessionRepository.findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate(
                        organizationId, academicYearId, sectionId, attendanceDate);
        if (existing.isPresent()) {
            enforceSectionAccess(actorUserId, existing.get());
            existing.get().requireDraft();
            return response(existing.get());
        }

        StudentAttendanceSession session = new StudentAttendanceSession(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                context.calendarDay().getId(),
                attendanceDate,
                actorUserId
        );
        try {
            StudentAttendanceSession saved = sessionRepository.saveAndFlush(session);
            enforceSectionAccess(actorUserId, saved);
            return response(saved);
        } catch (DataIntegrityViolationException exception) {
            StudentAttendanceSession concurrent =
                    sessionRepository.findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate(
                            organizationId, academicYearId, sectionId, attendanceDate)
                    .orElseThrow(() -> exception);
            return response(concurrent);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAttendanceSessionResponse getSession(
            long organizationId,
            long actorUserId,
            long sessionId
    ) {
        StudentAttendanceSession session = session(organizationId, sessionId);
        enforceSectionAccess(actorUserId, session);
        return response(session);
    }

    @Override
    @Transactional
    public StudentAttendanceSessionResponse saveDraftRecords(
            long organizationId,
            long actorUserId,
            long sessionId,
            BulkStudentAttendanceRecordRequest request
    ) {
        StudentAttendanceSession session = session(organizationId, sessionId);
        enforceSectionAccess(actorUserId, session);
        validateMutationSession(session, actorUserId);
        session.requireDraft();
        List<StudentAttendanceRecordRequest> requests = validateBulkRequest(request);

        Map<Long, StudentEnrollment> enrollments =
                enrollmentRepository.findEligibleForAttendanceDateByIds(
                                session.getOrganizationId(),
                                session.getAcademicYearId(),
                                session.getGradeLevelId(),
                                session.getSectionId(),
                                session.getAttendanceDate(),
                                requests.stream()
                                        .map(StudentAttendanceRecordRequest::studentEnrollmentId)
                                        .toList()
                        ).stream()
                        .collect(Collectors.toMap(StudentEnrollment::getId, Function.identity()));
        if (enrollments.size() != requests.size()) {
            throw new InvalidStudentAttendanceRecordingException(
                    "All student enrollments must belong to the session section and cover the attendance date"
            );
        }
        Set<Long> studentUserIds = new HashSet<>();
        for (StudentEnrollment enrollment : enrollments.values()) {
            if (!studentUserIds.add(enrollment.getStudentUserId())) {
                throw new InvalidStudentAttendanceRecordingException(
                        "Duplicate student user IDs are not allowed"
                );
            }
        }

        Map<Long, StudentAttendanceRecord> existing =
                recordRepository.findAllByAttendanceSessionIdAndStudentEnrollmentIdIn(
                                session.getId(),
                                enrollments.keySet()
                        ).stream()
                        .collect(Collectors.toMap(StudentAttendanceRecord::getStudentEnrollmentId, Function.identity()));
        Map<AttendanceStatus, StudentAttendanceStatusPolicy> credits = statusCredits(session.getOrganizationId());
        StudentAttendancePolicy policy = policy(session.getOrganizationId());
        List<StudentAttendanceRecord> saved = new ArrayList<>();
        Map<Long, Integer> requestLateCounts = new HashMap<>();

        for (StudentAttendanceRecordRequest item : requests) {
            StudentEnrollment enrollment = enrollments.get(item.studentEnrollmentId());
            AttendanceStatus effective = effectiveStatus(
                    session,
                    policy,
                    enrollment.getId(),
                    item.recordedStatus(),
                    requestLateCounts
            );
            boolean penaltyApplied = item.recordedStatus() == AttendanceStatus.LATE
                    && effective != AttendanceStatus.LATE;
            StudentAttendanceStatusPolicy credit = credits.get(effective);
            StudentAttendanceRecord record = existing.get(item.studentEnrollmentId());
            if (record == null) {
                record = new StudentAttendanceRecord(
                        session,
                        enrollment.getId(),
                        enrollment.getStudentUserId(),
                        item.recordedStatus(),
                        effective,
                        credit.getEarnedCredit(),
                        credit.getPossibleCredit(),
                        penaltyApplied,
                        item.remarks(),
                        actorUserId
                );
            } else {
                record.replace(
                        item.recordedStatus(),
                        effective,
                        credit.getEarnedCredit(),
                        credit.getPossibleCredit(),
                        penaltyApplied,
                        item.remarks(),
                        actorUserId
                );
            }
            saved.add(record);
        }

        recordRepository.deleteByAttendanceSessionIdAndStudentEnrollmentIdNotIn(
                session.getId(),
                requests.stream().map(StudentAttendanceRecordRequest::studentEnrollmentId).toList()
        );
        List<StudentAttendanceRecord> persisted = recordRepository.saveAll(saved);
        session.touch(actorUserId);
        return mapper.toResponse(session, persisted);
    }

    @Override
    @Transactional
    public StudentAttendanceSessionResponse submitManually(
            long organizationId,
            long actorUserId,
            long sessionId
    ) {
        StudentAttendanceSession session = session(organizationId, sessionId);
        enforceSectionAccess(actorUserId, session);
        validateMutationSession(session, actorUserId);
        session.requireDraft();
        List<StudentAttendanceRecord> records =
                recordRepository.findAllByAttendanceSessionIdOrderByIdAsc(session.getId());
        if (records.isEmpty()) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Attendance session cannot be submitted without records"
            );
        }
        Set<Long> markedEnrollmentIds = records.stream()
                .map(StudentAttendanceRecord::getStudentEnrollmentId)
                .collect(Collectors.toSet());
        List<StudentEnrollment> required = requiredEnrollments(session);
        if (!markedEnrollmentIds.containsAll(required.stream().map(StudentEnrollment::getId).toList())) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Attendance is missing for one or more students enrolled on the attendance date"
            );
        }
        session.submitManually(actorUserId);

        notificationOutboxService.createForSubmittedSession(
                session,
                records
        );

        return mapper.toResponse(session, records);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAttendanceSessionResponse getSectionAttendanceForDate(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate
    ) {
        validateReadContext(organizationId, academicYearId, gradeLevelId, sectionId, attendanceDate, actorUserId);
        StudentAttendanceSession session =
                sessionRepository.findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate(
                                organizationId, academicYearId, sectionId, attendanceDate)
                        .orElseThrow(() -> new StudentAttendanceSessionNotFoundException(
                                "Attendance session was not found"
                        ));
        enforceSectionAccess(actorUserId, session);
        return response(session);
    }

    private AttendanceValidationContext validateMutationContext(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate,
            long actorUserId
    ) {
        AttendanceValidationContext context = validateReadContext(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                attendanceDate,
                actorUserId
        );
        validateAcademicYearActive(context.academicYear());
        validateAllowedEntryDate(organizationId, attendanceDate);
        return context;
    }

    private void validateMutationSession(
            StudentAttendanceSession session,
            long actorUserId
    ) {
        AttendanceValidationContext context = validateReadContext(
                session.getOrganizationId(),
                session.getAcademicYearId(),
                session.getGradeLevelId(),
                session.getSectionId(),
                session.getAttendanceDate(),
                actorUserId
        );
        validateAcademicYearActive(context.academicYear());
        validateAllowedEntryDate(
                session.getOrganizationId(),
                session.getAttendanceDate()
        );
    }

    private AttendanceValidationContext validateReadContext(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate,
            long actorUserId
    ) {
        if (attendanceDate == null) {
            throw new InvalidStudentAttendanceRecordingException("Attendance date is required");
        }
        AcademicYear year = academicYearRepository.findByIdAndOrganizationId(academicYearId, organizationId)
                .orElseThrow(() -> new AcademicYearNotFoundException("Academic year not found"));
        if (attendanceDate.isBefore(year.getStartDate()) || attendanceDate.isAfter(year.getEndDate())) {
            throw new InvalidStudentAttendanceRecordingException("Attendance date must be inside the academic year");
        }
        if (sectionRepository.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                sectionId, gradeLevelId, academicYearId, organizationId).isEmpty()) {
            throw new SectionNotFoundException("Section not found");
        }
        AcademicCalendarDay calendarDay =
                eligibleCalendarDay(organizationId, academicYearId, attendanceDate);
        enforceSectionAccess(actorUserId, organizationId, academicYearId, gradeLevelId, sectionId);
        return new AttendanceValidationContext(year, calendarDay);
    }

    private void validateAcademicYearActive(AcademicYear year) {
        if (year.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new StudentAttendanceRecordingConflictException(
                    "Attendance can only be changed for an active academic year"
            );
        }
    }

    private void validateAllowedEntryDate(
            long organizationId,
            LocalDate attendanceDate
    ) {
        ZoneId schoolZone = schoolTimeZoneClient.getTimeZone(organizationId);
        LocalDate schoolToday = LocalDate.now(clock.withZone(schoolZone));
        if (attendanceDate.isAfter(schoolToday)) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Attendance date cannot be in the future"
            );
        }
        if (attendanceDate.isEqual(schoolToday)) {
            return;
        }
        StudentAttendancePolicy policy = policyForBackdatedEntry(organizationId);
        Set<String> authorities = currentAuthorities();
        boolean leadership = authorities.contains("ROLE_ADMIN")
                || authorities.contains("ROLE_PRINCIPAL")
                || authorities.contains("ROLE_VICE_PRINCIPAL");
        int allowedDays = leadership
                ? policy.getLeadershipBackEntryDays()
                : policy.getTeacherBackEntryDays();
        if (!Boolean.TRUE.equals(policy.getDeferredEntryEnabled()) || allowedDays <= 0) {
            throw new StudentAttendanceRecordingConflictException(
                    "Backdated attendance entry is not enabled"
            );
        }
        LocalDate oldestAllowed = schoolToday.minusDays(allowedDays);
        if (attendanceDate.isBefore(oldestAllowed)) {
            throw new StudentAttendanceRecordingConflictException(
                    "Attendance date is outside the allowed backdated entry window"
            );
        }
    }

    private StudentAttendancePolicy policyForBackdatedEntry(long organizationId) {
        try {
            return policy(organizationId);
        } catch (UnsupportedOperationException exception) {
            return new StudentAttendancePolicy(
                    organizationId,
                    com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode.DAILY,
                    java.time.DayOfWeek.MONDAY,
                    10,
                    20,
                    false,
                    3,
                    com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome.HALF_DAY,
                    com.dawnrise.academic.studentattendance.policy.enums.LateCountingPeriod.MONTHLY,
                    true,
                    30,
                    30,
                    false,
                    1L
            );
        }
    }

    private AcademicCalendarDay eligibleCalendarDay(
            long organizationId,
            long academicYearId,
            LocalDate attendanceDate
    ) {
        AcademicCalendarDay day = calendarDayRepository.findByOrganizationIdAndAcademicYearIdAndCalendarDate(
                        organizationId, academicYearId, attendanceDate)
                .orElseThrow(() -> new InvalidStudentAttendanceRecordingException(
                        "Academic calendar day is required for attendance recording"
                ));
        if (day.getAttendanceRequirement() == AttendanceRequirement.NOT_APPLICABLE
                || !day.isCountsTowardPercentage()) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Attendance can only be recorded for required counting calendar days"
            );
        }
        return day;
    }

    private List<StudentAttendanceRecordRequest> validateBulkRequest(
            BulkStudentAttendanceRecordRequest request
    ) {
        if (request == null || request.records() == null || request.records().isEmpty()) {
            throw new InvalidStudentAttendanceRecordingException("Attendance records are required");
        }
        if (request.records().size() > MAX_BULK_RECORDS) {
            throw new InvalidStudentAttendanceRecordingException("At most 100 attendance records can be saved at once");
        }
        Set<Long> enrollmentIds = new HashSet<>();
        for (StudentAttendanceRecordRequest item : request.records()) {
            if (item == null) {
                throw new InvalidStudentAttendanceRecordingException("Attendance record is required");
            }
            if (!enrollmentIds.add(item.studentEnrollmentId())) {
                throw new InvalidStudentAttendanceRecordingException("Duplicate student enrollment IDs are not allowed");
            }
        }
        return request.records();
    }

    private AttendanceStatus effectiveStatus(
            StudentAttendanceSession session,
            StudentAttendancePolicy policy,
            Long enrollmentId,
            AttendanceStatus recorded,
            Map<Long, Integer> requestLateCounts
    ) {
        if (recorded != AttendanceStatus.LATE) {
            return recorded;
        }
        YearMonth month = YearMonth.from(session.getAttendanceDate());
        int currentRequestLate = requestLateCounts.merge(enrollmentId, 1, Integer::sum);
        long priorLate = recordRepository.countPriorRecordedStatus(
                session.getOrganizationId(),
                session.getAcademicYearId(),
                enrollmentId,
                AttendanceStatus.LATE,
                month.atDay(1),
                month.atEndOfMonth(),
                session.getAttendanceDate()
        );
        return latePenaltyCalculator.effectiveStatus(
                recorded,
                Math.toIntExact(priorLate) + currentRequestLate,
                policy.getLatePenaltyEnabled(),
                policy.getLateOccurrencesThreshold(),
                policy.getLatePenaltyOutcome()
        );
    }

    private StudentAttendanceSession session(long organizationId, long sessionId) {
        return sessionRepository.findByIdAndOrganizationId(sessionId, organizationId)
                .orElseThrow(() -> new StudentAttendanceSessionNotFoundException(
                        "Attendance session was not found"
                ));
    }

    private StudentAttendancePolicy policy(long organizationId) {
        return policyRepository.findById(organizationId)
                .orElseThrow(() -> new InvalidStudentAttendanceRecordingException(
                        "Student attendance policy is required"
                ));
    }

    private Map<AttendanceStatus, StudentAttendanceStatusPolicy> statusCredits(long organizationId) {
        Map<AttendanceStatus, StudentAttendanceStatusPolicy> credits =
                statusPolicyRepository.findAllByOrganizationId(organizationId)
                        .stream()
                        .collect(Collectors.toMap(StudentAttendanceStatusPolicy::getAttendanceStatus, Function.identity()));
        if (!credits.keySet().containsAll(AttendanceStatus.finalStatuses())) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Student attendance status policy is incomplete"
            );
        }
        return credits;
    }

    private List<StudentEnrollment> requiredEnrollments(StudentAttendanceSession session) {
        return enrollmentRepository.findEligibleForAttendanceDate(
                session.getOrganizationId(),
                session.getAcademicYearId(),
                session.getGradeLevelId(),
                session.getSectionId(),
                session.getAttendanceDate()
        );
    }

    private StudentAttendanceSessionResponse response(StudentAttendanceSession session) {
        return mapper.toResponse(
                session,
                recordRepository.findAllByAttendanceSessionIdOrderByIdAsc(session.getId())
        );
    }

    private void enforceSectionAccess(long actorUserId, StudentAttendanceSession session) {
        enforceSectionAccess(actorUserId, session.getOrganizationId(), session.getAcademicYearId(),
                session.getGradeLevelId(), session.getSectionId());
    }

    private void enforceSectionAccess(
            long actorUserId,
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    ) {
        Set<String> authorities = currentAuthorities();
        if (authorities.contains("ROLE_ADMIN")
                || authorities.contains("ROLE_PRINCIPAL")
                || authorities.contains("ROLE_VICE_PRINCIPAL")) {
            return;
        }
        if (authorities.contains("ROLE_TEACHER")
                && teacherAssignmentRepository.existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserId(
                organizationId, academicYearId, gradeLevelId, sectionId, actorUserId)) {
            return;
        }
        throw new AccessDeniedException("Access Denied");
    }

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                ? Set.of()
                : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private record AttendanceValidationContext(
            AcademicYear academicYear,
            AcademicCalendarDay calendarDay
    ) {
    }
}
