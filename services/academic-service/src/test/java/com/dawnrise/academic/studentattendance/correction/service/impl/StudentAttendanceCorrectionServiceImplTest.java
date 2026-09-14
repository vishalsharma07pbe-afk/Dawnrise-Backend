package com.dawnrise.academic.studentattendance.correction.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.studentattendance.correction.dto.CreateStudentAttendanceCorrectionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionDecisionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionItemRequest;
import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionItem;
import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionRequest;
import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;
import com.dawnrise.academic.studentattendance.correction.exception.InvalidStudentAttendanceCorrectionException;
import com.dawnrise.academic.studentattendance.correction.exception.StudentAttendanceCorrectionConflictException;
import com.dawnrise.academic.studentattendance.correction.mapper.StudentAttendanceCorrectionMapper;
import com.dawnrise.academic.studentattendance.correction.repository.StudentAttendanceCorrectionItemRepository;
import com.dawnrise.academic.studentattendance.correction.repository.StudentAttendanceCorrectionRequestRepository;
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationOutboxService;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendancePolicy;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendanceStatusPolicy;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.enums.LateCountingPeriod;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendanceStatusPolicyRepository;
import com.dawnrise.academic.studentattendance.policy.service.StudentLatePenaltyCalculator;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceRecordRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StudentAttendanceCorrectionServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACADEMIC_YEAR_ID = 20L;
    private static final long GRADE_LEVEL_ID = 30L;
    private static final long SECTION_ID = 40L;
    private static final long REQUESTER_ID = 50L;
    private static final long REVIEWER_ID = 60L;
    private static final long SESSION_ID = 70L;
    private static final long RECORD_ID = 80L;

    private RequestRepositoryStub requestRepository;
    private ItemRepositoryStub itemRepository;
    private SessionRepositoryStub sessionRepository;
    private RecordRepositoryStub recordRepository;
    private StudentAttendancePolicy policy;
    private StudentAttendanceNotificationOutboxService
            notificationOutboxService;
    private StudentAttendanceCorrectionServiceImpl service;

    @BeforeEach
    void setUp() {
        requestRepository = new RequestRepositoryStub();
        itemRepository = new ItemRepositoryStub();
        sessionRepository = new SessionRepositoryStub();
        recordRepository = new RecordRepositoryStub();
        notificationOutboxService =
                mock(StudentAttendanceNotificationOutboxService.class);
        policy = policy(false, 3);
        service = new StudentAttendanceCorrectionServiceImpl(
                requestRepository.repository(),
                itemRepository.repository(),
                sessionRepository.repository(),
                recordRepository.repository(),
                academicYearRepository(),
                policyRepository(),
                statusPolicyRepository(),
                teacherAssignmentRepository(),
                new StudentLatePenaltyCalculator(),
                new StudentAttendanceCorrectionMapper(),
                notificationOutboxService,
                Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneId.of("UTC"))
        );
        authenticate("ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherListUsesSectionScopedRepositoryQuery() {
        authenticate("ROLE_TEACHER");
        StudentAttendanceCorrectionRequest request = pendingRequest();
        requestRepository.teacherScopedRequests = List.of(request);
        itemRepository.items = List.of();

        var response = service.list(
                ORGANIZATION_ID,
                REQUESTER_ID,
                null,
                StudentAttendanceCorrectionStatus.PENDING,
                PageRequest.of(0, 10)
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(requestRepository.teacherScopedCalls).isEqualTo(1);
        assertThat(requestRepository.tenantCalls).isZero();
    }

    @Test
    void createStoresImmutableSnapshotAndDoesNotMutateSubmittedRecord() {
        StudentAttendanceSession session = submittedSession();
        StudentAttendanceRecord record = record();
        ReflectionTestUtils.setField(record, "version", 1L);
        sessionRepository.session = Optional.of(session);
        recordRepository.records = List.of(record);

        var response = service.create(
                ORGANIZATION_ID,
                REQUESTER_ID,
                SESSION_ID,
                new CreateStudentAttendanceCorrectionRequest(
                        " Wrong mark ",
                        List.of(new StudentAttendanceCorrectionItemRequest(
                                RECORD_ID,
                                1L,
                                AttendanceStatus.PRESENT,
                                " Corrected "
                        ))
                )
        );

        assertThat(response.status()).isEqualTo(StudentAttendanceCorrectionStatus.PENDING);
        assertThat(response.reason()).isEqualTo("Wrong mark");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().previousRecordedStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(response.items().getFirst().proposedRecordedStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(response.items().getFirst().proposedRemarks()).isEqualTo("Corrected");
        assertThat(record.getRecordedStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(recordRepository.saveAllAndFlushCalls).isZero();
    }

    @Test
    void createRejectsNullProposedStatusBeforeSavingRequest() {
        sessionRepository.session = Optional.of(submittedSession());

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                REQUESTER_ID,
                SESSION_ID,
                new CreateStudentAttendanceCorrectionRequest(
                        "Wrong mark",
                        List.of(new StudentAttendanceCorrectionItemRequest(
                                RECORD_ID,
                                1L,
                                null,
                                null
                        ))
                )
        )).isInstanceOf(InvalidStudentAttendanceCorrectionException.class)
                .hasMessage("Proposed attendance status is required");
        assertThat(requestRepository.saveAndFlushCalls).isZero();
    }

    @Test
    void createRejectsBlankProposedRemarksBeforeSavingRequest() {
        sessionRepository.session = Optional.of(submittedSession());

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                REQUESTER_ID,
                SESSION_ID,
                new CreateStudentAttendanceCorrectionRequest(
                        "Wrong mark",
                        List.of(new StudentAttendanceCorrectionItemRequest(
                                RECORD_ID,
                                1L,
                                AttendanceStatus.PRESENT,
                                "   "
                        ))
                )
        )).isInstanceOf(InvalidStudentAttendanceCorrectionException.class)
                .hasMessage("Proposed remarks cannot be blank");
        assertThat(requestRepository.saveAndFlushCalls).isZero();
    }

    @Test
    void createRejectsExactNoOpCorrectionBeforeSavingRequest() {
        StudentAttendanceSession session = submittedSession();
        StudentAttendanceRecord record = record();
        ReflectionTestUtils.setField(record, "version", 1L);
        sessionRepository.session = Optional.of(session);
        recordRepository.records = List.of(record);

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                REQUESTER_ID,
                SESSION_ID,
                new CreateStudentAttendanceCorrectionRequest(
                        "Wrong mark",
                        List.of(new StudentAttendanceCorrectionItemRequest(
                                RECORD_ID,
                                1L,
                                AttendanceStatus.ABSENT,
                                "Original"
                        ))
                )
        )).isInstanceOf(InvalidStudentAttendanceCorrectionException.class)
                .hasMessage("Correction item must change the attendance snapshot");
        assertThat(requestRepository.saveAndFlushCalls).isZero();
    }

    @Test
    void createAllowsSameRecordedStatusWhenPolicyChangesEffectiveSnapshot() {
        policy = policy(true, 1);
        StudentAttendanceSession session = submittedSession();
        StudentAttendanceRecord record = lateRecord();
        ReflectionTestUtils.setField(record, "version", 1L);
        sessionRepository.session = Optional.of(session);
        recordRepository.records = List.of(record);

        var response = service.create(
                ORGANIZATION_ID,
                REQUESTER_ID,
                SESSION_ID,
                new CreateStudentAttendanceCorrectionRequest(
                        "Apply late policy",
                        List.of(new StudentAttendanceCorrectionItemRequest(
                                RECORD_ID,
                                1L,
                                AttendanceStatus.LATE,
                                "Original"
                        ))
                )
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().previousRecordedStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(response.items().getFirst().proposedRecordedStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(response.items().getFirst().previousEffectiveStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(response.items().getFirst().proposedEffectiveStatus()).isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(response.items().getFirst().proposedEarnedCredit()).isEqualByComparingTo("0.50");
        assertThat(response.items().getFirst().proposedLatePenaltyApplied()).isTrue();
    }

    @Test
    void approveRejectsRequesterSelfReview() {
        StudentAttendanceCorrectionRequest request = pendingRequest();
        requestRepository.lockedRequest = Optional.of(request);
        sessionRepository.lockedSession = Optional.of(submittedSession());

        assertThatThrownBy(() -> service.approve(
                ORGANIZATION_ID,
                REQUESTER_ID,
                request.getId(),
                new StudentAttendanceCorrectionDecisionRequest(0L, null)
        )).isInstanceOf(StudentAttendanceCorrectionConflictException.class)
                .hasMessage("Requester cannot review their own correction");
    }

    @Test
    void approveRejectsStaleAttendanceRecordVersion() {
        StudentAttendanceCorrectionRequest request = pendingRequest();
        StudentAttendanceRecord record = record();
        ReflectionTestUtils.setField(record, "version", 2L);
        requestRepository.lockedRequest = Optional.of(request);
        sessionRepository.lockedSession = Optional.of(submittedSession());
        itemRepository.items = List.of(item(record, 1L));
        recordRepository.lockedRecords = List.of(record);

        assertThatThrownBy(() -> service.approve(
                ORGANIZATION_ID,
                REVIEWER_ID,
                request.getId(),
                new StudentAttendanceCorrectionDecisionRequest(0L, null)
        )).isInstanceOf(StudentAttendanceCorrectionConflictException.class)
                .hasMessage("Attendance record version is stale");
        assertThat(recordRepository.saveAllAndFlushCalls).isZero();
        verifyNoInteractions(notificationOutboxService);
    }

    @Test
    void approveAppliesItemsFlushesRecordsAndCreatesOutboxEventBeforeCompletingRequest() {
        StudentAttendanceCorrectionRequest request = pendingRequest();
        StudentAttendanceRecord record = record();
        ReflectionTestUtils.setField(record, "version", 1L);
        StudentAttendanceSession session = submittedSession();
        requestRepository.lockedRequest = Optional.of(request);
        sessionRepository.lockedSession = Optional.of(session);
        itemRepository.items = List.of(item(record, 1L));
        recordRepository.lockedRecords = List.of(record);

        var response = service.approve(
                ORGANIZATION_ID,
                REVIEWER_ID,
                request.getId(),
                new StudentAttendanceCorrectionDecisionRequest(0L, null)
        );

        assertThat(record.getRecordedStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(record.getUpdatedByUserId()).isEqualTo(REVIEWER_ID);
        assertThat(recordRepository.saveAllAndFlushCalls).isEqualTo(1);
        assertThat(response.status()).isEqualTo(StudentAttendanceCorrectionStatus.APPROVED);
        assertThat(response.reviewedByUserId()).isEqualTo(REVIEWER_ID);
        verify(notificationOutboxService).createForApprovedCorrection(
                session,
                request.getId(),
                itemRepository.items,
                Map.of(record.getId(), record),
                OffsetDateTime.parse("2026-09-12T08:00:00Z")
        );
    }

    @Test
    void approveAcceptsReviewComment() {
        StudentAttendanceCorrectionRequest request = pendingRequest();
        StudentAttendanceRecord record = record();
        ReflectionTestUtils.setField(record, "version", 1L);
        requestRepository.lockedRequest = Optional.of(request);
        sessionRepository.lockedSession = Optional.of(submittedSession());
        itemRepository.items = List.of(item(record, 1L));
        recordRepository.lockedRecords = List.of(record);

        var response = service.approve(
                ORGANIZATION_ID,
                REVIEWER_ID,
                request.getId(),
                new StudentAttendanceCorrectionDecisionRequest(0L, "Reviewed")
        );

        assertThat(response.status()).isEqualTo(StudentAttendanceCorrectionStatus.APPROVED);
        assertThat(response.reviewComment()).isEqualTo("Reviewed");
    }

    @Test
    void outboxFailurePropagatesFromCorrectionApprovalTransaction() {
        StudentAttendanceCorrectionRequest request = pendingRequest();
        StudentAttendanceRecord record = record();
        ReflectionTestUtils.setField(record, "version", 1L);
        StudentAttendanceSession session = submittedSession();
        requestRepository.lockedRequest = Optional.of(request);
        sessionRepository.lockedSession = Optional.of(session);
        itemRepository.items = List.of(item(record, 1L));
        recordRepository.lockedRecords = List.of(record);
        doThrow(new IllegalStateException("outbox failed"))
                .when(notificationOutboxService)
                .createForApprovedCorrection(
                        session,
                        request.getId(),
                        itemRepository.items,
                        Map.of(record.getId(), record),
                        OffsetDateTime.parse("2026-09-12T08:00:00Z")
                );

        assertThatThrownBy(() -> service.approve(
                ORGANIZATION_ID,
                REVIEWER_ID,
                request.getId(),
                new StudentAttendanceCorrectionDecisionRequest(0L, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox failed");
    }

    @Test
    void rejectRequiresReviewComment() {
        StudentAttendanceCorrectionRequest request = pendingRequest();
        requestRepository.lockedRequest = Optional.of(request);

        assertThatThrownBy(() -> service.reject(
                ORGANIZATION_ID,
                REVIEWER_ID,
                request.getId(),
                new StudentAttendanceCorrectionDecisionRequest(0L, null)
        )).isInstanceOf(InvalidStudentAttendanceCorrectionException.class)
                .hasMessage("Review comment is required");
    }

    @Test
    void rejectAcceptsReviewComment() {
        StudentAttendanceCorrectionRequest request = pendingRequest();
        requestRepository.lockedRequest = Optional.of(request);

        var response = service.reject(
                ORGANIZATION_ID,
                REVIEWER_ID,
                request.getId(),
                new StudentAttendanceCorrectionDecisionRequest(0L, "Needs evidence")
        );

        assertThat(response.status()).isEqualTo(StudentAttendanceCorrectionStatus.REJECTED);
        assertThat(response.reviewedByUserId()).isEqualTo(REVIEWER_ID);
        assertThat(response.reviewComment()).isEqualTo("Needs evidence");
    }

    @Test
    void cancelAcceptsMissingReviewComment() {
        StudentAttendanceCorrectionRequest request = pendingRequest();
        requestRepository.lockedRequest = Optional.of(request);

        var response = service.cancel(
                ORGANIZATION_ID,
                REQUESTER_ID,
                request.getId(),
                new StudentAttendanceCorrectionDecisionRequest(0L, null)
        );

        assertThat(response.status()).isEqualTo(StudentAttendanceCorrectionStatus.CANCELLED);
        assertThat(response.reviewedByUserId()).isNull();
        assertThat(response.reviewComment()).isNull();
    }

    private static void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "actor",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }

    private static StudentAttendanceCorrectionRequest pendingRequest() {
        StudentAttendanceCorrectionRequest request = new StudentAttendanceCorrectionRequest(
                submittedSession(),
                "Wrong mark",
                REQUESTER_ID,
                OffsetDateTime.parse("2026-09-12T07:00:00Z")
        );
        ReflectionTestUtils.setField(request, "id", 90L);
        ReflectionTestUtils.setField(request, "version", 0L);
        return request;
    }

    private static StudentAttendanceCorrectionItem item(
            StudentAttendanceRecord record,
            Long expectedRecordVersion
    ) {
        return new StudentAttendanceCorrectionItem(
                90L,
                record,
                expectedRecordVersion,
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                BigDecimal.ONE,
                BigDecimal.ONE,
                false,
                "Corrected"
        );
    }

    private static StudentAttendanceRecord record() {
        StudentAttendanceRecord record = new StudentAttendanceRecord(
                submittedSession(),
                100L,
                110L,
                AttendanceStatus.ABSENT,
                AttendanceStatus.ABSENT,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                false,
                "Original",
                REQUESTER_ID
        );
        ReflectionTestUtils.setField(record, "id", RECORD_ID);
        return record;
    }

    private static StudentAttendanceRecord lateRecord() {
        StudentAttendanceRecord record = new StudentAttendanceRecord(
                submittedSession(),
                100L,
                110L,
                AttendanceStatus.LATE,
                AttendanceStatus.LATE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                false,
                "Original",
                REQUESTER_ID
        );
        ReflectionTestUtils.setField(record, "id", RECORD_ID);
        return record;
    }

    private static StudentAttendanceSession submittedSession() {
        StudentAttendanceSession session = new StudentAttendanceSession(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                120L,
                LocalDate.of(2026, 9, 12),
                REQUESTER_ID
        );
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "version", 0L);
        session.submitManually(REQUESTER_ID);
        return session;
    }

    private static AcademicYearRepository academicYearRepository() {
        AcademicYear year = new AcademicYear(
                ORGANIZATION_ID,
                "2026",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31)
        );
        ReflectionTestUtils.setField(year, "id", ACADEMIC_YEAR_ID);
        ReflectionTestUtils.setField(year, "status", AcademicYearStatus.ACTIVE);
        return proxy(AcademicYearRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findByIdAndOrganizationId" -> Optional.of(year);
            default -> defaultObjectMethod(proxy, method.getName(), args);
        });
    }

    private static StudentAttendancePolicy policy(boolean latePenaltyEnabled, int lateOccurrencesThreshold) {
        return new StudentAttendancePolicy(
                ORGANIZATION_ID,
                AttendanceMode.DAILY,
                DayOfWeek.MONDAY,
                10,
                20,
                latePenaltyEnabled,
                lateOccurrencesThreshold,
                LatePenaltyOutcome.HALF_DAY,
                LateCountingPeriod.MONTHLY,
                REQUESTER_ID
        );
    }

    private StudentAttendancePolicyRepository policyRepository() {
        return proxy(StudentAttendancePolicyRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.of(this.policy);
            default -> defaultObjectMethod(proxy, method.getName(), args);
        });
    }

    private static StudentAttendanceStatusPolicyRepository statusPolicyRepository() {
        Map<AttendanceStatus, StudentAttendanceStatusPolicy> policies = Map.of(
                AttendanceStatus.PRESENT,
                new StudentAttendanceStatusPolicy(ORGANIZATION_ID, AttendanceStatus.PRESENT, BigDecimal.ONE, BigDecimal.ONE),
                AttendanceStatus.ABSENT,
                new StudentAttendanceStatusPolicy(ORGANIZATION_ID, AttendanceStatus.ABSENT, BigDecimal.ZERO, BigDecimal.ONE),
                AttendanceStatus.LATE,
                new StudentAttendanceStatusPolicy(ORGANIZATION_ID, AttendanceStatus.LATE, BigDecimal.ONE, BigDecimal.ONE),
                AttendanceStatus.HALF_DAY,
                new StudentAttendanceStatusPolicy(ORGANIZATION_ID, AttendanceStatus.HALF_DAY, new BigDecimal("0.50"), BigDecimal.ONE),
                AttendanceStatus.EXCUSED,
                new StudentAttendanceStatusPolicy(ORGANIZATION_ID, AttendanceStatus.EXCUSED, BigDecimal.ONE, BigDecimal.ONE)
        );
        return proxy(StudentAttendanceStatusPolicyRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findAllByOrganizationId" -> List.copyOf(policies.values());
            default -> defaultObjectMethod(proxy, method.getName(), args);
        });
    }

    private static TeacherAssignmentRepository teacherAssignmentRepository() {
        return proxy(TeacherAssignmentRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserId" -> true;
            default -> defaultObjectMethod(proxy, method.getName(), args);
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }

    private static InvocationHandler unsupported() {
        return (proxy, method, args) -> defaultObjectMethod(proxy, method.getName(), args);
    }

    private static Object defaultObjectMethod(Object proxy, String methodName, Object[] args) {
        if (methodName.equals("hashCode")) {
            return System.identityHashCode(proxy);
        }
        if (methodName.equals("equals")) {
            return proxy == args[0];
        }
        if (methodName.equals("toString")) {
            return "RepositoryStub";
        }
        throw new UnsupportedOperationException(methodName);
    }

    private static final class RequestRepositoryStub {
        private Optional<StudentAttendanceCorrectionRequest> lockedRequest = Optional.empty();
        private List<StudentAttendanceCorrectionRequest> teacherScopedRequests = List.of();
        private int teacherScopedCalls;
        private int tenantCalls;
        private int saveAndFlushCalls;

        private StudentAttendanceCorrectionRequestRepository repository() {
            return proxy(StudentAttendanceCorrectionRequestRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByIdAndOrganizationIdForUpdate" -> lockedRequest;
                case "existsByOrganizationIdAndAttendanceSessionIdAndStatus" -> false;
                case "saveAndFlush" -> {
                    saveAndFlushCalls++;
                    StudentAttendanceCorrectionRequest request = (StudentAttendanceCorrectionRequest) args[0];
                    ReflectionTestUtils.setField(request, "id", 90L);
                    ReflectionTestUtils.setField(request, "version", 0L);
                    yield request;
                }
                case "findTeacherScopedRequests" -> {
                    teacherScopedCalls++;
                    yield new PageImpl<>(teacherScopedRequests);
                }
                case "findTenantRequests" -> {
                    tenantCalls++;
                    yield new PageImpl<>(List.of());
                }
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static final class ItemRepositoryStub {
        private List<StudentAttendanceCorrectionItem> items = List.of();

        private StudentAttendanceCorrectionItemRepository repository() {
            return proxy(StudentAttendanceCorrectionItemRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findAllByCorrectionRequestIdOrderByIdAsc" -> items;
                case "saveAllAndFlush" -> {
                    items = List.copyOf((Collection<StudentAttendanceCorrectionItem>) args[0]);
                    yield items;
                }
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static final class SessionRepositoryStub {
        private Optional<StudentAttendanceSession> session = Optional.empty();
        private Optional<StudentAttendanceSession> lockedSession = Optional.empty();

        private StudentAttendanceSessionRepository repository() {
            return proxy(StudentAttendanceSessionRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByIdAndOrganizationId" -> session;
                case "findByIdAndOrganizationIdForUpdate" -> lockedSession;
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static final class RecordRepositoryStub {
        private List<StudentAttendanceRecord> records = List.of();
        private List<StudentAttendanceRecord> lockedRecords = List.of();
        private int saveAllAndFlushCalls;

        private StudentAttendanceRecordRepository repository() {
            return proxy(StudentAttendanceRecordRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findAllByOrganizationIdAndAttendanceSessionIdAndIdIn" -> records;
                case "findAllByOrganizationIdAndAttendanceSessionIdAndIdInForUpdate" -> lockedRecords;
                case "countPriorRecordedStatus" -> 0L;
                case "saveAllAndFlush" -> {
                    saveAllAndFlushCalls++;
                    yield (Collection<?>) args[0];
                }
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }
}
