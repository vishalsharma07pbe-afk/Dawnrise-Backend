package com.dawnrise.academic.studentattendance.policy.service.impl;

import com.dawnrise.academic.studentattendance.config.StudentAttendanceProperties;
import com.dawnrise.academic.studentattendance.policy.dto.AttendanceStatusPolicyRequest;
import com.dawnrise.academic.studentattendance.policy.dto.StudentAttendancePolicyRequest;
import com.dawnrise.academic.studentattendance.policy.dto.StudentAttendancePolicyResponse;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendancePolicy;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendanceStatusPolicy;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.exception.InvalidStudentAttendancePolicyException;
import com.dawnrise.academic.studentattendance.policy.exception.StudentAttendancePolicyConflictException;
import com.dawnrise.academic.studentattendance.policy.exception.StudentAttendancePolicyNotFoundException;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendanceStatusPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendancePolicyServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACTOR_USER_ID = 20L;

    private PolicyRepositoryStub policyRepositoryStub;
    private StatusPolicyRepositoryStub statusRepositoryStub;
    private StudentAttendancePolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        policyRepositoryStub = new PolicyRepositoryStub();
        statusRepositoryStub = new StatusPolicyRepositoryStub();
        service = new StudentAttendancePolicyServiceImpl(
                policyRepositoryStub.repository(),
                statusRepositoryStub.repository(),
                properties()
        );
    }

    @Test
    void initializationUsesEveryConfiguredHeaderDefault() {
        StudentAttendancePolicyResponse response =
                service.initialize(ORGANIZATION_ID, ACTOR_USER_ID);

        assertThat(response.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(response.attendanceMode()).isEqualTo(AttendanceMode.DAILY);
        assertThat(response.weekStartDay()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(response.draftWarningMinutes()).isEqualTo(17);
        assertThat(response.automaticSubmissionMinutes()).isEqualTo(43);
        assertThat(policyRepositoryStub.savedPolicy.getUpdatedByUserId())
                .isEqualTo(ACTOR_USER_ID);
    }

    @Test
    void initializationCreatesExactlyFiveConfiguredStatusRows() {
        service.initialize(ORGANIZATION_ID, ACTOR_USER_ID);

        assertThat(statusRepositoryStub.savedStatuses)
                .hasSize(5)
                .extracting(StudentAttendanceStatusPolicy::getAttendanceStatus)
                .containsExactlyInAnyOrderElementsOf(
                        AttendanceStatus.finalStatuses()
                );
        assertInitializedCredit(AttendanceStatus.PRESENT, "1.00", "1.00");
        assertInitializedCredit(AttendanceStatus.ABSENT, "0.00", "1.00");
        assertInitializedCredit(AttendanceStatus.LATE, "1.00", "1.00");
        assertInitializedCredit(AttendanceStatus.HALF_DAY, "0.50", "1.00");
        assertInitializedCredit(AttendanceStatus.EXCUSED, "0.00", "0.00");
    }

    @Test
    void unmarkedIsNeverCreatedDuringInitialization() {
        service.initialize(ORGANIZATION_ID, ACTOR_USER_ID);

        assertThat(statusRepositoryStub.savedStatuses)
                .extracting(StudentAttendanceStatusPolicy::getAttendanceStatus)
                .doesNotContain(AttendanceStatus.UNMARKED);
    }

    @Test
    void existingPolicyInitializationReturnsConflictWithoutWrites() {
        policyRepositoryStub.exists = true;

        assertThatThrownBy(() ->
                service.initialize(ORGANIZATION_ID, ACTOR_USER_ID))
                .isInstanceOf(StudentAttendancePolicyConflictException.class)
                .hasMessage("Student attendance policy is already configured");

        assertThat(policyRepositoryStub.saveAndFlushCalls).isZero();
        assertThat(statusRepositoryStub.saveAllAndFlushCalls).isZero();
    }

    @Test
    void concurrentInsertFailureTranslatesToConflictWithCause() {
        DataIntegrityViolationException cause =
                new DataIntegrityViolationException("duplicate policy");
        policyRepositoryStub.saveAndFlushException = cause;

        assertThatThrownBy(() ->
                service.initialize(ORGANIZATION_ID, ACTOR_USER_ID))
                .isInstanceOf(StudentAttendancePolicyConflictException.class)
                .hasCause(cause);
    }

    @Test
    void failureWhileSavingStatusRowsIsPartOfTransactionalInitializer() throws Exception {
        RuntimeException failure = new RuntimeException("status save failed");
        statusRepositoryStub.saveAllAndFlushException = failure;

        assertThatThrownBy(() ->
                service.initialize(ORGANIZATION_ID, ACTOR_USER_ID))
                .isSameAs(failure);

        Transactional annotation = StudentAttendancePolicyServiceImpl.class
                .getMethod("initialize", long.class, long.class)
                .getAnnotation(Transactional.class);
        assertThat(annotation).isNull();
        assertThat(StudentAttendancePolicyServiceImpl.class
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void putMissingPolicyReturnsNotFound() {
        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                validRequest(0L)
        )).isInstanceOf(StudentAttendancePolicyNotFoundException.class)
                .hasMessage("Student attendance policy is not configured");
    }

    @Test
    void putRequiresExpectedVersion() {
        policyRepositoryStub.foundPolicy = Optional.of(existingPolicy());

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                validRequest(null)
        )).isInstanceOf(InvalidStudentAttendancePolicyException.class)
                .hasMessage("Expected version is required when updating the policy");
    }

    @Test
    void putVersionZeroIsAccepted() {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, 0L);
        policyRepositoryStub.foundPolicy = Optional.of(policy);
        statusRepositoryStub.foundStatuses = initialStatuses();
        policyRepositoryStub.incrementVersionRows = 1;
        policyRepositoryStub.reloadedPolicy = policyWithVersion(1L);

        StudentAttendancePolicyResponse response = service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                validRequest(0L)
        );

        assertThat(response.statusPolicies()).hasSize(5);
        assertThat(policyRepositoryStub.saveAndFlushCalls).isEqualTo(1);
        assertThat(policyRepositoryStub.incrementVersionCalls).isEqualTo(1);
        assertThat(statusRepositoryStub.saveAllAndFlushCalls).isEqualTo(1);
    }

    @Test
    void changingRootHeaderIncrementsVersionExactlyOnce() {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, 0L);
        policyRepositoryStub.foundPolicy = Optional.of(policy);
        policyRepositoryStub.incrementVersionOnSave = true;
        policyRepositoryStub.reloadedPolicy = policyWithVersion(1L);
        statusRepositoryStub.foundStatuses = initialStatuses();

        StudentAttendancePolicyResponse response = service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                request(
                        0L,
                        DayOfWeek.TUESDAY,
                        10,
                        20,
                        standardStatuses()
                )
        );

        assertThat(response.version()).isEqualTo(1L);
        assertThat(policyRepositoryStub.saveAndFlushCalls).isEqualTo(1);
        assertThat(policyRepositoryStub.incrementVersionCalls).isZero();
        assertThat(policyRepositoryStub.findByIdCalls).isEqualTo(2);
    }

    @Test
    void changingOnlyChildStatusIncrementsRootVersionExactlyOnce() {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, 0L);
        policyRepositoryStub.foundPolicy = Optional.of(policy);
        policyRepositoryStub.incrementVersionRows = 1;
        policyRepositoryStub.reloadedPolicy = policyWithVersion(1L);
        statusRepositoryStub.foundStatuses = initialStatuses();

        StudentAttendancePolicyResponse response = service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                request(
                        0L,
                        DayOfWeek.MONDAY,
                        10,
                        20,
                        List.of(
                                status(AttendanceStatus.PRESENT, "1.00", "1.00"),
                                status(AttendanceStatus.ABSENT, "0.00", "1.00"),
                                status(AttendanceStatus.LATE, "1.00", "1.00"),
                                status(AttendanceStatus.HALF_DAY, "0.50", "1.00"),
                                status(AttendanceStatus.EXCUSED, "0.00", "0.00")
                        )
                )
        );

        assertThat(response.version()).isEqualTo(1L);
        assertThat(policyRepositoryStub.incrementVersionCalls).isEqualTo(1);
        assertThat(policyRepositoryStub.lastIncrementExpectedVersion)
                .isEqualTo(0L);
        assertThat(statusRepositoryStub.saveAllAndFlushCalls).isEqualTo(1);
    }

    @Test
    void sameValuesFromSameActorStillIncrementRootVersionOnce() {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, 0L);
        policyRepositoryStub.foundPolicy = Optional.of(policy);
        policyRepositoryStub.incrementVersionRows = 1;
        policyRepositoryStub.reloadedPolicy = policyWithVersion(1L);
        statusRepositoryStub.foundStatuses = initialStatuses();

        StudentAttendancePolicyResponse response = service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                validRequest(0L)
        );

        assertThat(response.version()).isEqualTo(1L);
        assertThat(policyRepositoryStub.incrementVersionCalls).isEqualTo(1);
        assertThat(statusRepositoryStub.saveAllAndFlushCalls).isEqualTo(1);
    }

    @Test
    void atomicIncrementAffectingZeroRowsReturnsConflict() {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, 0L);
        policyRepositoryStub.foundPolicy = Optional.of(policy);
        policyRepositoryStub.incrementVersionRows = 0;
        statusRepositoryStub.foundStatuses = initialStatuses();

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                validRequest(0L)
        )).isInstanceOf(StudentAttendancePolicyConflictException.class)
                .hasMessage("Student attendance policy was modified by another request");

        assertThat(policyRepositoryStub.incrementVersionCalls).isEqualTo(1);
        assertThat(statusRepositoryStub.saveAllAndFlushCalls).isZero();
    }

    @Test
    void staleExpectedVersionReturnsConflictBeforeChildWrites() {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, 1L);
        policyRepositoryStub.foundPolicy = Optional.of(policy);

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                validRequest(0L)
        )).isInstanceOf(StudentAttendancePolicyConflictException.class)
                .hasMessage("Student attendance policy was modified by another request");

        assertThat(policyRepositoryStub.saveAndFlushCalls).isZero();
        assertThat(policyRepositoryStub.incrementVersionCalls).isZero();
        assertThat(statusRepositoryStub.saveAllAndFlushCalls).isZero();
    }

    @Test
    void statusRowFailureOccursAfterAggregateVersionClaimInTransaction()
            throws Exception {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, 0L);
        policyRepositoryStub.foundPolicy = Optional.of(policy);
        policyRepositoryStub.incrementVersionRows = 1;
        policyRepositoryStub.reloadedPolicy = policyWithVersion(1L);
        statusRepositoryStub.foundStatuses = initialStatuses();
        RuntimeException failure = new RuntimeException("status save failed");
        statusRepositoryStub.saveAllAndFlushException = failure;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                validRequest(0L)
        )).isSameAs(failure);

        assertThat(policyRepositoryStub.incrementVersionCalls).isEqualTo(1);
        assertThat(statusRepositoryStub.saveAllAndFlushCalls).isEqualTo(1);
        Transactional annotation = StudentAttendancePolicyServiceImpl.class
                .getMethod(
                        "update",
                        long.class,
                        long.class,
                        StudentAttendancePolicyRequest.class
                )
                .getAnnotation(Transactional.class);
        assertThat(annotation).isNull();
        assertThat(StudentAttendancePolicyServiceImpl.class
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void responseContainsReloadedRootVersion() {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, 0L);
        policyRepositoryStub.foundPolicy = Optional.of(policy);
        policyRepositoryStub.incrementVersionRows = 1;
        policyRepositoryStub.reloadedPolicy = policyWithVersion(7L);
        statusRepositoryStub.foundStatuses = initialStatuses();

        StudentAttendancePolicyResponse response = service.update(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                validRequest(0L)
        );

        assertThat(response.version()).isEqualTo(7L);
    }

    @Test
    void getPerformsNoWrites() {
        policyRepositoryStub.foundPolicy = Optional.of(existingPolicy());
        statusRepositoryStub.foundStatuses = initialStatuses();

        StudentAttendancePolicyResponse response = service.get(ORGANIZATION_ID);

        assertThat(response.statusPolicies()).hasSize(5);
        assertThat(policyRepositoryStub.saveAndFlushCalls).isZero();
        assertThat(statusRepositoryStub.saveAllAndFlushCalls).isZero();
        assertThat(policyRepositoryStub.existsByIdCalls).isZero();
    }

    private static StudentAttendancePolicyRequest validRequest(Long version) {
        return request(
                version,
                DayOfWeek.MONDAY,
                10,
                20,
                standardStatuses()
        );
    }

    private static StudentAttendancePolicyRequest request(
            Long version,
            DayOfWeek weekStartDay,
            int draftWarningMinutes,
            int automaticSubmissionMinutes,
            List<AttendanceStatusPolicyRequest> statusPolicies
    ) {
        return new StudentAttendancePolicyRequest(
                AttendanceMode.DAILY,
                weekStartDay,
                draftWarningMinutes,
                automaticSubmissionMinutes,
                version,
                statusPolicies
        );
    }

    private static List<AttendanceStatusPolicyRequest> standardStatuses() {
        return List.of(
                status(AttendanceStatus.PRESENT, "1.00", "1.00"),
                status(AttendanceStatus.ABSENT, "0.00", "1.00"),
                status(AttendanceStatus.LATE, "0.50", "1.00"),
                status(AttendanceStatus.HALF_DAY, "0.50", "1.00"),
                status(AttendanceStatus.EXCUSED, "0.00", "0.00")
        );
    }

    private static AttendanceStatusPolicyRequest status(
            AttendanceStatus status,
            String earned,
            String possible
    ) {
        return new AttendanceStatusPolicyRequest(
                status,
                new BigDecimal(earned),
                new BigDecimal(possible)
        );
    }

    private static StudentAttendancePolicy existingPolicy() {
        return new StudentAttendancePolicy(
                ORGANIZATION_ID,
                AttendanceMode.DAILY,
                DayOfWeek.MONDAY,
                10,
                20,
                ACTOR_USER_ID
        );
    }

    private static StudentAttendancePolicy policyWithVersion(Long version) {
        StudentAttendancePolicy policy = existingPolicy();
        setVersion(policy, version);
        return policy;
    }

    private static void setVersion(
            StudentAttendancePolicy policy,
            Long version
    ) {
        try {
            Field field = StudentAttendancePolicy.class
                    .getDeclaredField("version");
            field.setAccessible(true);
            field.set(policy, version);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<StudentAttendanceStatusPolicy> initialStatuses() {
        return List.of(
                new StudentAttendanceStatusPolicy(
                        ORGANIZATION_ID,
                        AttendanceStatus.PRESENT,
                        new BigDecimal("1.00"),
                        new BigDecimal("1.00")
                ),
                new StudentAttendanceStatusPolicy(
                        ORGANIZATION_ID,
                        AttendanceStatus.ABSENT,
                        new BigDecimal("0.00"),
                        new BigDecimal("1.00")
                ),
                new StudentAttendanceStatusPolicy(
                        ORGANIZATION_ID,
                        AttendanceStatus.LATE,
                        new BigDecimal("0.50"),
                        new BigDecimal("1.00")
                ),
                new StudentAttendanceStatusPolicy(
                        ORGANIZATION_ID,
                        AttendanceStatus.HALF_DAY,
                        new BigDecimal("0.50"),
                        new BigDecimal("1.00")
                ),
                new StudentAttendanceStatusPolicy(
                        ORGANIZATION_ID,
                        AttendanceStatus.EXCUSED,
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00")
                )
        );
    }

    private static StudentAttendanceProperties properties() {
        StudentAttendanceProperties properties = new StudentAttendanceProperties();
        properties.setMaxRecordsPerRequest(200);
        properties.setMaxCalendarDaysPerAcademicYear(400);
        properties.setDefaultAttendanceMode(AttendanceMode.DAILY);
        properties.setDefaultWeekStartDay(DayOfWeek.SUNDAY);
        properties.setDefaultDraftWarningMinutes(17);
        properties.setDefaultAutomaticSubmissionMinutes(43);
        properties.setDefaultWorkingDayWeight(new BigDecimal("1.00"));
        properties.setDefaultStatusCredits(MapFactory.defaultCredits());
        return properties;
    }

    private static class MapFactory {
        private static java.util.Map<AttendanceStatus, StudentAttendanceProperties.StatusCredit> defaultCredits() {
            java.util.Map<AttendanceStatus, StudentAttendanceProperties.StatusCredit> credits =
                    new java.util.EnumMap<>(AttendanceStatus.class);
            credits.put(AttendanceStatus.PRESENT, credit("1.00", "1.00"));
            credits.put(AttendanceStatus.ABSENT, credit("0.00", "1.00"));
            credits.put(AttendanceStatus.LATE, credit("1.00", "1.00"));
            credits.put(AttendanceStatus.HALF_DAY, credit("0.50", "1.00"));
            credits.put(AttendanceStatus.EXCUSED, credit("0.00", "0.00"));
            return credits;
        }

        private static StudentAttendanceProperties.StatusCredit credit(
                String earned,
                String possible
        ) {
            StudentAttendanceProperties.StatusCredit credit =
                    new StudentAttendanceProperties.StatusCredit();
            credit.setEarnedCredit(new BigDecimal(earned));
            credit.setPossibleCredit(new BigDecimal(possible));
            return credit;
        }
    }

    private void assertInitializedCredit(
            AttendanceStatus attendanceStatus,
            String earned,
            String possible
    ) {
        assertThat(statusRepositoryStub.savedStatuses)
                .filteredOn(status -> status.getAttendanceStatus()
                        == attendanceStatus)
                .singleElement()
                .satisfies(status -> {
                    assertThat(status.getEarnedCredit())
                            .isEqualByComparingTo(new BigDecimal(earned));
                    assertThat(status.getPossibleCredit())
                            .isEqualByComparingTo(new BigDecimal(possible));
                });
    }

    private static class PolicyRepositoryStub {
        private boolean exists;
        private Optional<StudentAttendancePolicy> foundPolicy = Optional.empty();
        private StudentAttendancePolicy reloadedPolicy;
        private StudentAttendancePolicy savedPolicy;
        private RuntimeException saveAndFlushException;
        private boolean incrementVersionOnSave;
        private int incrementVersionRows = 1;
        private Long lastIncrementExpectedVersion;
        private int existsByIdCalls;
        private int saveAndFlushCalls;
        private int incrementVersionCalls;
        private int findByIdCalls;

        private StudentAttendancePolicyRepository repository() {
            return (StudentAttendancePolicyRepository) Proxy.newProxyInstance(
                    StudentAttendancePolicyRepository.class.getClassLoader(),
                    new Class<?>[]{StudentAttendancePolicyRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "existsById" -> {
                            existsByIdCalls++;
                            yield exists;
                        }
                        case "findById" -> {
                            findByIdCalls++;
                            if (findByIdCalls > 1 && reloadedPolicy != null) {
                                yield Optional.of(reloadedPolicy);
                            }
                            yield foundPolicy;
                        }
                        case "saveAndFlush" -> {
                            saveAndFlushCalls++;
                            if (saveAndFlushException != null) {
                                throw saveAndFlushException;
                            }
                            savedPolicy = (StudentAttendancePolicy) args[0];
                            if (incrementVersionOnSave) {
                                setVersion(
                                        savedPolicy,
                                        savedPolicy.getVersion() + 1
                                );
                            }
                            yield savedPolicy;
                        }
                        case "incrementVersionIfCurrent" -> {
                            incrementVersionCalls++;
                            lastIncrementExpectedVersion = (Long) args[1];
                            yield incrementVersionRows;
                        }
                        case "toString" -> "PolicyRepositoryStub";
                        default -> throw new UnsupportedOperationException(
                                method.getName()
                        );
                    }
            );
        }
    }

    private static class StatusPolicyRepositoryStub {
        private List<StudentAttendanceStatusPolicy> foundStatuses = List.of();
        private List<StudentAttendanceStatusPolicy> savedStatuses =
                new ArrayList<>();
        private RuntimeException saveAllAndFlushException;
        private int saveAllAndFlushCalls;

        private StudentAttendanceStatusPolicyRepository repository() {
            return (StudentAttendanceStatusPolicyRepository)
                    Proxy.newProxyInstance(
                            StudentAttendanceStatusPolicyRepository.class
                                    .getClassLoader(),
                            new Class<?>[]{
                                    StudentAttendanceStatusPolicyRepository.class
                            },
                            (proxy, method, args) -> switch (method.getName()) {
                                case "findAllByOrganizationId" -> foundStatuses;
                                case "saveAllAndFlush" -> {
                                    saveAllAndFlushCalls++;
                                    if (saveAllAndFlushException != null) {
                                        throw saveAllAndFlushException;
                                    }
                                    savedStatuses =
                                            new ArrayList<>((List<StudentAttendanceStatusPolicy>) args[0]);
                                    yield savedStatuses;
                                }
                                case "toString" -> "StatusPolicyRepositoryStub";
                                default -> throw new UnsupportedOperationException(
                                        method.getName()
                                );
                            }
                    );
        }
    }
}
