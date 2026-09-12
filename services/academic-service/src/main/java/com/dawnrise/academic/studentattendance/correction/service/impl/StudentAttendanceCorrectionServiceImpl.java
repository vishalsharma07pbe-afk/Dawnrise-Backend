package com.dawnrise.academic.studentattendance.correction.service.impl;

import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.studentattendance.correction.dto.CreateStudentAttendanceCorrectionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionDecisionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionItemRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionResponse;
import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionItem;
import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionRequest;
import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;
import com.dawnrise.academic.studentattendance.correction.exception.InvalidStudentAttendanceCorrectionException;
import com.dawnrise.academic.studentattendance.correction.exception.StudentAttendanceCorrectionConflictException;
import com.dawnrise.academic.studentattendance.correction.exception.StudentAttendanceCorrectionNotFoundException;
import com.dawnrise.academic.studentattendance.correction.mapper.StudentAttendanceCorrectionMapper;
import com.dawnrise.academic.studentattendance.correction.repository.StudentAttendanceCorrectionItemRepository;
import com.dawnrise.academic.studentattendance.correction.repository.StudentAttendanceCorrectionRequestRepository;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendancePolicy;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendanceStatusPolicy;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendanceStatusPolicyRepository;
import com.dawnrise.academic.studentattendance.policy.service.StudentLatePenaltyCalculator;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceRecordRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentAttendanceCorrectionServiceImpl implements com.dawnrise.academic.studentattendance.correction.service.StudentAttendanceCorrectionService {

    private static final int MAX_ITEMS = 100;

    private final StudentAttendanceCorrectionRequestRepository requestRepository;
    private final StudentAttendanceCorrectionItemRepository itemRepository;
    private final StudentAttendanceSessionRepository sessionRepository;
    private final StudentAttendanceRecordRepository recordRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentAttendancePolicyRepository policyRepository;
    private final StudentAttendanceStatusPolicyRepository statusPolicyRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final StudentLatePenaltyCalculator latePenaltyCalculator;
    private final StudentAttendanceCorrectionMapper mapper;
    private final Clock clock;

    public StudentAttendanceCorrectionServiceImpl(
            StudentAttendanceCorrectionRequestRepository requestRepository,
            StudentAttendanceCorrectionItemRepository itemRepository,
            StudentAttendanceSessionRepository sessionRepository,
            StudentAttendanceRecordRepository recordRepository,
            AcademicYearRepository academicYearRepository,
            StudentAttendancePolicyRepository policyRepository,
            StudentAttendanceStatusPolicyRepository statusPolicyRepository,
            TeacherAssignmentRepository teacherAssignmentRepository,
            StudentLatePenaltyCalculator latePenaltyCalculator,
            StudentAttendanceCorrectionMapper mapper,
            Clock clock
    ) {
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.academicYearRepository = academicYearRepository;
        this.policyRepository = policyRepository;
        this.statusPolicyRepository = statusPolicyRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.latePenaltyCalculator = latePenaltyCalculator;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StudentAttendanceCorrectionResponse create(
            long organizationId,
            long actorUserId,
            long attendanceSessionId,
            CreateStudentAttendanceCorrectionRequest request
    ) {
        StudentAttendanceSession session = sessionRepository.findByIdAndOrganizationId(attendanceSessionId, organizationId)
                .orElseThrow(() -> new StudentAttendanceCorrectionNotFoundException("Attendance session was not found"));
        enforceAccess(actorUserId, session);
        enforceYearPolicyForCreate(actorUserId, session);
        if (session.getLifecycleStatus() != StudentAttendanceSessionStatus.SUBMITTED) {
            throw new StudentAttendanceCorrectionConflictException("Only submitted attendance can be corrected");
        }
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new InvalidStudentAttendanceCorrectionException("Correction items are required");
        }
        if (request.items().size() > MAX_ITEMS) {
            throw new InvalidStudentAttendanceCorrectionException("At most 100 correction items are allowed");
        }
        if (requestRepository.existsByOrganizationIdAndAttendanceSessionIdAndStatus(
                organizationId, attendanceSessionId, StudentAttendanceCorrectionStatus.PENDING)) {
            throw new StudentAttendanceCorrectionConflictException("A pending correction already exists for this session");
        }
        Map<Long, StudentAttendanceCorrectionItemRequest> itemRequests = new LinkedHashMap<>();
        for (StudentAttendanceCorrectionItemRequest item : request.items()) {
            if (item == null || item.attendanceRecordId() == null) {
                throw new InvalidStudentAttendanceCorrectionException("Attendance record ID is required");
            }
            if (item.expectedRecordVersion() == null) {
                throw new InvalidStudentAttendanceCorrectionException("Expected attendance record version is required");
            }
            if (item.proposedRecordedStatus() == null) {
                throw new InvalidStudentAttendanceCorrectionException("Proposed attendance status is required");
            }
            if (!AttendanceStatus.finalStatuses().contains(item.proposedRecordedStatus())) {
                throw new InvalidStudentAttendanceCorrectionException("Proposed attendance status is not supported");
            }
            if (item.proposedRemarks() != null && item.proposedRemarks().trim().isEmpty()) {
                throw new InvalidStudentAttendanceCorrectionException("Proposed remarks cannot be blank");
            }
            if (itemRequests.putIfAbsent(item.attendanceRecordId(), item) != null) {
                throw new InvalidStudentAttendanceCorrectionException("Duplicate attendance record IDs are not allowed");
            }
        }
        Map<Long, StudentAttendanceRecord> records = recordRepository
                .findAllByOrganizationIdAndAttendanceSessionIdAndIdIn(
                        organizationId,
                        attendanceSessionId,
                        itemRequests.keySet()
                )
                .stream()
                .collect(Collectors.toMap(StudentAttendanceRecord::getId, Function.identity()));
        if (records.size() != itemRequests.size()) {
            throw new InvalidStudentAttendanceCorrectionException("All attendance records must belong to the submitted session");
        }

        StudentAttendancePolicy policy = policy(organizationId);
        Map<AttendanceStatus, StudentAttendanceStatusPolicy> credits = statusCredits(organizationId);
        List<PreparedCorrectionItem> preparedItems = new ArrayList<>();
        Map<Long, Integer> requestLateCounts = new HashMap<>();
        for (StudentAttendanceCorrectionItemRequest itemRequest : itemRequests.values()) {
            StudentAttendanceRecord record = records.get(itemRequest.attendanceRecordId());
            if (!Objects.equals(record.getVersion(), itemRequest.expectedRecordVersion())) {
                throw new StudentAttendanceCorrectionConflictException("Attendance record version is stale");
            }
            AttendanceStatus effective = effectiveStatus(
                    session,
                    policy,
                    record.getStudentEnrollmentId(),
                    itemRequest.proposedRecordedStatus(),
                    requestLateCounts
            );
            boolean penaltyApplied = itemRequest.proposedRecordedStatus() == AttendanceStatus.LATE
                    && effective != AttendanceStatus.LATE;
            StudentAttendanceStatusPolicy credit = credits.get(effective);
            String proposedRemarks = itemRequest.proposedRemarks() == null
                    ? null
                    : itemRequest.proposedRemarks().trim();
            if (!hasSnapshotChange(
                    record,
                    itemRequest.proposedRecordedStatus(),
                    effective,
                    credit.getEarnedCredit(),
                    credit.getPossibleCredit(),
                    penaltyApplied,
                    proposedRemarks
            )) {
                throw new InvalidStudentAttendanceCorrectionException(
                        "Correction item must change the attendance snapshot"
                );
            }
            preparedItems.add(new PreparedCorrectionItem(
                    record,
                    record.getVersion(),
                    itemRequest.proposedRecordedStatus(),
                    effective,
                    credit.getEarnedCredit(),
                    credit.getPossibleCredit(),
                    penaltyApplied,
                    proposedRemarks
            ));
        }

        StudentAttendanceCorrectionRequest correction = requestRepository.saveAndFlush(
                new StudentAttendanceCorrectionRequest(
                        session,
                        request.reason(),
                        actorUserId,
                        OffsetDateTime.now(clock)
                )
        );
        List<StudentAttendanceCorrectionItem> items = preparedItems.stream()
                .map(item -> new StudentAttendanceCorrectionItem(
                        correction.getId(),
                        item.record(),
                        item.expectedAttendanceRecordVersion(),
                        item.proposedRecordedStatus(),
                        item.proposedEffectiveStatus(),
                        item.proposedEarnedCredit(),
                        item.proposedPossibleCredit(),
                        item.proposedLatePenaltyApplied(),
                        item.proposedRemarks()
                ))
                .toList();
        try {
            return mapper.toResponse(correction, itemRepository.saveAllAndFlush(items));
        } catch (DataIntegrityViolationException exception) {
            throw new StudentAttendanceCorrectionConflictException("A pending correction already exists for this session");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAttendanceCorrectionResponse get(long organizationId, long actorUserId, long requestId) {
        StudentAttendanceCorrectionRequest request = request(organizationId, requestId);
        enforceAccess(actorUserId, request);
        return response(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StudentAttendanceCorrectionResponse> list(
            long organizationId,
            long actorUserId,
            Long attendanceSessionId,
            StudentAttendanceCorrectionStatus status,
            Pageable pageable
    ) {
        Page<StudentAttendanceCorrectionRequest> requests = isLeadership()
                ? requestRepository.findTenantRequests(organizationId, attendanceSessionId, status, pageable)
                : requestRepository.findTeacherScopedRequests(organizationId, actorUserId, attendanceSessionId, status, pageable);
        return requests.map(this::response);
    }

    @Override
    @Transactional
    public StudentAttendanceCorrectionResponse cancel(
            long organizationId,
            long actorUserId,
            long requestId,
            StudentAttendanceCorrectionDecisionRequest decision
    ) {
        StudentAttendanceCorrectionRequest request = lockedRequest(organizationId, requestId);
        enforceAccess(actorUserId, request);
        request.cancel(actorUserId, expectedVersion(decision), decision.reviewComment(), OffsetDateTime.now(clock));
        return response(request);
    }

    @Override
    @Transactional
    public StudentAttendanceCorrectionResponse reject(
            long organizationId,
            long actorUserId,
            long requestId,
            StudentAttendanceCorrectionDecisionRequest decision
    ) {
        StudentAttendanceCorrectionRequest request = lockedRequest(organizationId, requestId);
        enforceAccess(actorUserId, request);
        request.reject(actorUserId, expectedVersion(decision), decision.reviewComment(), OffsetDateTime.now(clock));
        return response(request);
    }

    @Override
    @Transactional
    public StudentAttendanceCorrectionResponse approve(
            long organizationId,
            long actorUserId,
            long requestId,
            StudentAttendanceCorrectionDecisionRequest decision
    ) {
        StudentAttendanceCorrectionRequest request = lockedRequest(organizationId, requestId);
        enforceAccess(actorUserId, request);
        request.requirePending();
        StudentAttendanceSession session = sessionRepository
                .findByIdAndOrganizationIdForUpdate(request.getAttendanceSessionId(), organizationId)
                .orElseThrow(() -> new StudentAttendanceCorrectionNotFoundException("Attendance session was not found"));
        if (session.getLifecycleStatus() != StudentAttendanceSessionStatus.SUBMITTED) {
            throw new StudentAttendanceCorrectionConflictException("Attendance session is no longer submitted");
        }
        List<StudentAttendanceCorrectionItem> items =
                itemRepository.findAllByCorrectionRequestIdOrderByIdAsc(request.getId());
        Set<Long> recordIds = items.stream()
                .map(StudentAttendanceCorrectionItem::getAttendanceRecordId)
                .collect(Collectors.toSet());
        Map<Long, StudentAttendanceRecord> lockedRecords = recordRepository
                .findAllByOrganizationIdAndAttendanceSessionIdAndIdInForUpdate(
                        organizationId,
                        request.getAttendanceSessionId(),
                        recordIds
                )
                .stream()
                .collect(Collectors.toMap(StudentAttendanceRecord::getId, Function.identity()));
        if (lockedRecords.size() != items.size()) {
            throw new StudentAttendanceCorrectionConflictException("Attendance records changed before approval");
        }
        for (StudentAttendanceCorrectionItem item : items) {
            StudentAttendanceRecord record = lockedRecords.get(item.getAttendanceRecordId());
            if (!Objects.equals(record.getVersion(), item.getExpectedAttendanceRecordVersion())) {
                throw new StudentAttendanceCorrectionConflictException("Attendance record version is stale");
            }
            record.applyApprovedCorrection(
                    item.getProposedRecordedStatus(),
                    item.getProposedEffectiveStatus(),
                    item.getProposedEarnedCredit(),
                    item.getProposedPossibleCredit(),
                    item.isProposedLatePenaltyApplied(),
                    item.getProposedRemarks(),
                    actorUserId
            );
        }
        recordRepository.saveAllAndFlush(new ArrayList<>(lockedRecords.values()));
        request.approve(actorUserId, expectedVersion(decision), decision.reviewComment(), OffsetDateTime.now(clock));
        return response(request);
    }

    private Long expectedVersion(StudentAttendanceCorrectionDecisionRequest decision) {
        if (decision == null || decision.expectedVersion() == null) {
            throw new InvalidStudentAttendanceCorrectionException("Expected version is required");
        }
        return decision.expectedVersion();
    }

    private StudentAttendanceCorrectionRequest request(long organizationId, long requestId) {
        return requestRepository.findByIdAndOrganizationId(requestId, organizationId)
                .orElseThrow(() -> new StudentAttendanceCorrectionNotFoundException("Correction request was not found"));
    }

    private StudentAttendanceCorrectionRequest lockedRequest(long organizationId, long requestId) {
        return requestRepository.findByIdAndOrganizationIdForUpdate(requestId, organizationId)
                .orElseThrow(() -> new StudentAttendanceCorrectionNotFoundException("Correction request was not found"));
    }

    private StudentAttendanceCorrectionResponse response(StudentAttendanceCorrectionRequest request) {
        return mapper.toResponse(
                request,
                itemRepository.findAllByCorrectionRequestIdOrderByIdAsc(request.getId())
        );
    }

    private void enforceYearPolicyForCreate(long actorUserId, StudentAttendanceSession session) {
        var year = academicYearRepository
                .findByIdAndOrganizationId(session.getAcademicYearId(), session.getOrganizationId())
                .orElseThrow(() -> new InvalidStudentAttendanceCorrectionException("Academic year is required"));
        if (year.getStatus() == AcademicYearStatus.CLOSED && !isLeadership()) {
            throw new AccessDeniedException("Access Denied");
        }
        if (year.getStatus() != AcademicYearStatus.ACTIVE && year.getStatus() != AcademicYearStatus.CLOSED) {
            throw new StudentAttendanceCorrectionConflictException("Correction is only allowed for active or closed academic years");
        }
    }

    private void enforceAccess(long actorUserId, StudentAttendanceCorrectionRequest request) {
        enforceAccess(actorUserId, request.getOrganizationId(), request.getAcademicYearId(),
                request.getGradeLevelId(), request.getSectionId());
    }

    private void enforceAccess(long actorUserId, StudentAttendanceSession session) {
        enforceAccess(actorUserId, session.getOrganizationId(), session.getAcademicYearId(),
                session.getGradeLevelId(), session.getSectionId());
    }

    private void enforceAccess(long actorUserId, long organizationId, long academicYearId, long gradeLevelId, long sectionId) {
        if (isLeadership()) {
            return;
        }
        if (authorities().contains("ROLE_TEACHER")
                && teacherAssignmentRepository.existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserId(
                organizationId, academicYearId, gradeLevelId, sectionId, actorUserId)) {
            return;
        }
        throw new AccessDeniedException("Access Denied");
    }

    private boolean isLeadership() {
        Set<String> authorities = authorities();
        return authorities.contains("ROLE_ADMIN")
                || authorities.contains("ROLE_PRINCIPAL")
                || authorities.contains("ROLE_VICE_PRINCIPAL");
    }

    private Set<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? Set.of()
                : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private StudentAttendancePolicy policy(long organizationId) {
        return policyRepository.findById(organizationId)
                .orElseThrow(() -> new InvalidStudentAttendanceCorrectionException("Student attendance policy is required"));
    }

    private Map<AttendanceStatus, StudentAttendanceStatusPolicy> statusCredits(long organizationId) {
        Map<AttendanceStatus, StudentAttendanceStatusPolicy> credits =
                statusPolicyRepository.findAllByOrganizationId(organizationId)
                        .stream()
                        .collect(Collectors.toMap(StudentAttendanceStatusPolicy::getAttendanceStatus, Function.identity()));
        if (!credits.keySet().containsAll(AttendanceStatus.finalStatuses())) {
            throw new InvalidStudentAttendanceCorrectionException("Student attendance status policy is incomplete");
        }
        return credits;
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

    private boolean hasSnapshotChange(
            StudentAttendanceRecord record,
            AttendanceStatus proposedRecordedStatus,
            AttendanceStatus proposedEffectiveStatus,
            java.math.BigDecimal proposedEarnedCredit,
            java.math.BigDecimal proposedPossibleCredit,
            boolean proposedLatePenaltyApplied,
            String proposedRemarks
    ) {
        return record.getRecordedStatus() != proposedRecordedStatus
                || record.getEffectiveStatus() != proposedEffectiveStatus
                || record.getEarnedCredit().compareTo(proposedEarnedCredit) != 0
                || record.getPossibleCredit().compareTo(proposedPossibleCredit) != 0
                || record.isLatePenaltyApplied() != proposedLatePenaltyApplied
                || !Objects.equals(record.getRemarks(), proposedRemarks);
    }

    private record PreparedCorrectionItem(
            StudentAttendanceRecord record,
            Long expectedAttendanceRecordVersion,
            AttendanceStatus proposedRecordedStatus,
            AttendanceStatus proposedEffectiveStatus,
            java.math.BigDecimal proposedEarnedCredit,
            java.math.BigDecimal proposedPossibleCredit,
            boolean proposedLatePenaltyApplied,
            String proposedRemarks
    ) {
    }
}
