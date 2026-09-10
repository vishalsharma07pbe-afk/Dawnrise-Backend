package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConflictResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionItem;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionOperation;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionItemRepository;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionOperationRepository;
import com.dawnrise.academic.studentprogression.service.impl.StudentProgressionExecutorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentProgressionExecutorImplTest {

    private OperationRepositoryStub operationRepository;
    private EnrollmentRepositoryStub enrollmentRepository;
    private ItemRepositoryStub itemRepository;
    private StubPlanBuilder planBuilder;
    private StudentProgressionExecutorImpl executor;

    @BeforeEach
    void setUp() {
        operationRepository = new OperationRepositoryStub();
        enrollmentRepository = new EnrollmentRepositoryStub();
        itemRepository = new ItemRepositoryStub();
        planBuilder = new StubPlanBuilder();
        executor = new StudentProgressionExecutorImpl(
                operationRepository.repository(),
                itemRepository.repository(),
                enrollmentRepository.repository(),
                planBuilder,
                JsonMapper.builder().build()
        );
    }

    @Test
    void promotedAndRepeatedCompleteSourceAndCreateTargetEnrollments() {
        StudentEnrollment promoted = sourceEnrollment(101L, 1001L);
        StudentEnrollment repeated = sourceEnrollment(102L, 1002L);
        StudentProgressionConfirmationRequest request = request(
                promote(101L),
                repeat(102L)
        );
        operationRepository.operation =
                operation(100L, 2, hash('a'), hash('b'));
        planBuilder.plan = plan(hash('b'),
                item(promoted, promote(101L)),
                item(repeated, repeat(102L)));

        StudentProgressionConfirmationResponse response = execute(request);

        assertThat(promoted.getStatus())
                .isEqualTo(StudentEnrollmentStatus.COMPLETED);
        assertThat(repeated.getStatus())
                .isEqualTo(StudentEnrollmentStatus.COMPLETED);
        assertThat(enrollmentRepository.savedTargetEnrollments).hasSize(2);
        assertThat(response.counts().promoted()).isEqualTo(1);
        assertThat(response.counts().repeated()).isEqualTo(1);
    }

    @Test
    void graduatedCompletesSourceWithoutTargetEnrollment() {
        StudentEnrollment source = sourceEnrollment(101L, 1001L);
        StudentProgressionConfirmationRequest request =
                request(graduate(101L));
        planBuilder.plan = plan(hash('b'), item(source, graduate(101L)));

        StudentProgressionConfirmationResponse response = execute(request);

        assertThat(source.getStatus())
                .isEqualTo(StudentEnrollmentStatus.COMPLETED);
        assertThat(response.items().getFirst().targetEnrollmentId())
                .isNull();
        assertThat(enrollmentRepository.savedTargetEnrollments).isEmpty();
    }

    @Test
    void leftWithdrawsSourceWithoutTargetEnrollment() {
        StudentEnrollment source = sourceEnrollment(101L, 1001L);
        StudentProgressionConfirmationRequest request = request(left(101L));
        planBuilder.plan = plan(hash('b'), item(source, left(101L)));

        StudentProgressionConfirmationResponse response = execute(request);

        assertThat(source.getStatus())
                .isEqualTo(StudentEnrollmentStatus.WITHDRAWN);
        assertThat(response.counts().left()).isEqualTo(1);
        assertThat(enrollmentRepository.savedTargetEnrollments).isEmpty();
    }

    @Test
    void staleFingerprintRejectsBeforeEnrollmentMutation() {
        StudentEnrollment source = sourceEnrollment(101L, 1001L);
        StudentProgressionConfirmationRequest request = request(promote(101L));
        planBuilder.plan = plan(hash('c'), item(source, promote(101L)));

        assertThatThrownBy(() -> execute(request))
                .isInstanceOf(StudentProgressionConflictException.class)
                .hasMessage("Student progression preview is stale")
                .extracting("failureCode")
                .isEqualTo("STALE_PREVIEW");

        assertThat(source.getStatus())
                .isEqualTo(StudentEnrollmentStatus.ENROLLED);
        assertNoWrites();
    }

    @Test
    void blockingPlanConflictsRejectBeforeMutation() {
        StudentEnrollment source = sourceEnrollment(101L, 1001L);
        StudentProgressionDecisionRequest decision = promote(101L);
        StudentProgressionConfirmationRequest request = request(decision);
        planBuilder.plan = plan(hash('b'), new StudentProgressionPlanItem(
                decision,
                source,
                sourceGrade(),
                sourceSection(),
                targetGrade(),
                targetSection(),
                201L,
                301L,
                List.of(new StudentProgressionConflictResponse(
                        "TARGET_CONFLICT",
                        101L,
                        "targetRollNumber",
                        "Target conflict"
                ))
        ));

        assertThatThrownBy(() -> execute(request))
                .isInstanceOf(StudentProgressionConflictException.class)
                .hasMessage("Student progression contains blocking conflicts")
                .extracting("failureCode")
                .isEqualTo("PROGRESSION_CONFLICTS");

        assertThat(source.getStatus())
                .isEqualTo(StudentEnrollmentStatus.ENROLLED);
        assertNoWrites();
    }

    @Test
    void manualReviewCannotExecute() {
        StudentEnrollment source = sourceEnrollment(101L, 1001L);
        StudentProgressionDecisionRequest decision = manualReview(101L);
        StudentProgressionConfirmationRequest request = request(decision);
        planBuilder.plan = new StudentProgressionPlan(
                sourceYear(),
                targetYear(),
                "Annual promotion",
                List.of(new StudentProgressionPlanItem(
                        decision,
                        source,
                        sourceGrade(),
                        sourceSection(),
                        null,
                        null,
                        null,
                        null,
                        List.of()
                )),
                List.of(),
                hash('b')
        );

        assertThatThrownBy(() -> execute(request))
                .isInstanceOf(StudentProgressionConflictException.class)
                .hasMessage("Manual-review decisions cannot be confirmed");

        assertThat(source.getStatus())
                .isEqualTo(StudentEnrollmentStatus.ENROLLED);
        assertThat(enrollmentRepository.flushCalls).isZero();
        assertThat(itemRepository.savedProgressionItems).isEmpty();
    }

    @Test
    void progressionItemsLinkSourceAndCreatedTargetEnrollments() {
        StudentEnrollment source = sourceEnrollment(101L, 1001L);
        StudentProgressionConfirmationRequest request = request(promote(101L));
        planBuilder.plan = plan(hash('b'), item(source, promote(101L)));

        StudentProgressionConfirmationResponse response = execute(request);

        assertThat(itemRepository.savedProgressionItems).hasSize(1);
        StudentProgressionItem item =
                itemRepository.savedProgressionItems.getFirst();
        assertThat(item.getSourceEnrollmentId()).isEqualTo(101L);
        assertThat(item.getStudentUserId()).isEqualTo(1001L);
        assertThat(item.getTargetEnrollmentId()).isEqualTo(500L);
        assertThat(response.items().getFirst().targetEnrollmentId())
                .isEqualTo(500L);
    }

    @Test
    void successfulExecutionStoresResultJsonAndMarksOperationSucceeded()
            throws Exception {
        StudentEnrollment source = sourceEnrollment(101L, 1001L);
        StudentProgressionConfirmationRequest request = request(promote(101L));
        planBuilder.plan = plan(hash('b'), item(source, promote(101L)));

        StudentProgressionConfirmationResponse response = execute(request);

        assertThat(operationRepository.operation.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.SUCCEEDED);
        assertThat(operationRepository.operation.getResultJson()).isNotBlank();
        StudentProgressionConfirmationResponse stored =
                JsonMapper.builder().build().readValue(
                        operationRepository.operation.getResultJson(),
                        StudentProgressionConfirmationResponse.class
                );
        assertThat(stored).isEqualTo(response);
        assertThat(operationRepository.saved)
                .isSameAs(operationRepository.operation);
    }

    @Test
    void operationNotRunningIsRejected() {
        operationRepository.operation = operation(100L, 1, hash('a'), hash('b'));
        operationRepository.operation.markFailed("FAILED", "Failed");
        StudentProgressionConfirmationRequest request = request(promote(101L));

        assertThatThrownBy(() -> execute(request))
                .isInstanceOf(StudentProgressionConflictException.class)
                .hasMessage("Student progression operation is not running")
                .extracting("failureCode")
                .isEqualTo("FAILED");

        assertThat(planBuilder.calls).isZero();
        assertNoWrites();
    }

    @Test
    void requestHashYearFingerprintAndItemCountMismatchesAreRejected() {
        assertMismatch(operation(100L, 1, hash('a'), hash('b')),
                request(promote(101L)),
                hash('c'),
                "REQUEST_HASH_MISMATCH");

        assertMismatch(operation(100L, 1, hash('a'), hash('b')),
                new StudentProgressionConfirmationRequest(
                        9L,
                        "Annual promotion",
                        List.of(promote(101L)),
                        hash('b')
                ),
                hash('a'),
                "OPERATION_YEAR_MISMATCH");

        assertMismatch(operation(100L, 1, hash('a'), hash('b')),
                new StudentProgressionConfirmationRequest(
                        1L,
                        "Annual promotion",
                        List.of(promote(101L)),
                        hash('c')
                ),
                hash('a'),
                "OPERATION_FINGERPRINT_MISMATCH");

        assertMismatch(operation(100L, 2, hash('a'), hash('b')),
                request(promote(101L)),
                hash('a'),
                "OPERATION_ITEM_COUNT_MISMATCH");
    }

    private void assertMismatch(
            StudentProgressionOperation operation,
            StudentProgressionConfirmationRequest request,
            String requestHash,
            String failureCode
    ) {
        operationRepository.operation = operation;

        assertThatThrownBy(() -> executor.execute(
                10L,
                100L,
                2L,
                requestHash,
                request
        )).isInstanceOf(StudentProgressionConflictException.class)
                .extracting("failureCode")
                .isEqualTo(failureCode);

        assertNoWrites();
    }

    private StudentProgressionConfirmationResponse execute(
            StudentProgressionConfirmationRequest request
    ) {
        return executor.execute(10L, 100L, 2L, hash('a'), request);
    }

    private void assertNoWrites() {
        assertThat(enrollmentRepository.savedTargetEnrollments).isEmpty();
        assertThat(enrollmentRepository.savedSourceEnrollments).isEmpty();
        assertThat(enrollmentRepository.flushCalls).isZero();
        assertThat(itemRepository.savedProgressionItems).isEmpty();
        assertThat(operationRepository.saveAndFlushCalls).isZero();
    }

    private static StudentProgressionPlan plan(
            String previewFingerprint,
            StudentProgressionPlanItem... items
    ) {
        return new StudentProgressionPlan(
                sourceYear(),
                targetYear(),
                "Annual promotion",
                List.of(items),
                List.of(),
                previewFingerprint
        );
    }

    private static StudentProgressionPlanItem item(
            StudentEnrollment sourceEnrollment,
            StudentProgressionDecisionRequest decision
    ) {
        boolean target = decision.outcome() == StudentProgressionOutcome.PROMOTED
                || decision.outcome() == StudentProgressionOutcome.REPEATED;
        return new StudentProgressionPlanItem(
                decision,
                sourceEnrollment,
                sourceGrade(),
                sourceSection(),
                target ? targetGrade() : null,
                target ? targetSection() : null,
                target ? 201L : null,
                target ? 301L : null,
                List.of()
        );
    }

    private static StudentProgressionConfirmationRequest request(
            StudentProgressionDecisionRequest... decisions
    ) {
        return new StudentProgressionConfirmationRequest(
                1L,
                "Annual promotion",
                List.of(decisions),
                hash('b')
        );
    }

    private static StudentProgressionDecisionRequest promote(Long sourceId) {
        return decision(sourceId, StudentProgressionOutcome.PROMOTED);
    }

    private static StudentProgressionDecisionRequest repeat(Long sourceId) {
        return decision(sourceId, StudentProgressionOutcome.REPEATED);
    }

    private static StudentProgressionDecisionRequest graduate(Long sourceId) {
        return terminalDecision(sourceId, StudentProgressionOutcome.GRADUATED);
    }

    private static StudentProgressionDecisionRequest left(Long sourceId) {
        return terminalDecision(sourceId, StudentProgressionOutcome.LEFT);
    }

    private static StudentProgressionDecisionRequest manualReview(
            Long sourceId
    ) {
        return terminalDecision(
                sourceId,
                StudentProgressionOutcome.MANUAL_REVIEW
        );
    }

    private static StudentProgressionDecisionRequest terminalDecision(
            Long sourceId,
            StudentProgressionOutcome outcome
    ) {
        return new StudentProgressionDecisionRequest(
                sourceId,
                outcome,
                null,
                null,
                null,
                effectiveOn(),
                null
        );
    }

    private static StudentProgressionDecisionRequest decision(
            Long sourceId,
            StudentProgressionOutcome outcome
    ) {
        return new StudentProgressionDecisionRequest(
                sourceId,
                outcome,
                201L,
                301L,
                "A-" + sourceId,
                effectiveOn(),
                null
        );
    }

    private static StudentProgressionOperation operation(
            Long id,
            int totalItems,
            String requestHash,
            String previewFingerprint
    ) {
        StudentProgressionOperation operation =
                new StudentProgressionOperation(
                        10L,
                        1L,
                        2L,
                        "Annual promotion",
                        "key-1",
                        requestHash,
                        previewFingerprint,
                        42L,
                        totalItems
                );
        operation.markRunning();
        ReflectionTestUtils.setField(operation, "id", id);
        return operation;
    }

    private static AcademicYear sourceYear() {
        AcademicYear year = new AcademicYear(
                10L,
                "Source",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31)
        );
        ReflectionTestUtils.setField(year, "id", 1L);
        ReflectionTestUtils.setField(year, "status", AcademicYearStatus.CLOSED);
        return year;
    }

    private static AcademicYear targetYear() {
        AcademicYear year = new AcademicYear(
                10L,
                "Target",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
        ReflectionTestUtils.setField(year, "id", 2L);
        ReflectionTestUtils.setField(year, "status", AcademicYearStatus.PLANNED);
        return year;
    }

    private static StudentEnrollment sourceEnrollment(
            Long id,
            Long studentUserId
    ) {
        StudentEnrollment enrollment = new StudentEnrollment(
                10L,
                1L,
                200L,
                300L,
                studentUserId,
                "S-" + id,
                LocalDate.of(2025, 1, 1)
        );
        ReflectionTestUtils.setField(enrollment, "id", id);
        return enrollment;
    }

    private static GradeLevel sourceGrade() {
        return grade(200L, 1);
    }

    private static GradeLevel targetGrade() {
        return grade(201L, 2);
    }

    private static GradeLevel grade(Long id, int order) {
        GradeLevel grade =
                new GradeLevel(10L, 1L, "G" + order, "Grade " + order, order);
        ReflectionTestUtils.setField(grade, "id", id);
        return grade;
    }

    private static Section sourceSection() {
        return section(300L, 200L);
    }

    private static Section targetSection() {
        return section(301L, 201L);
    }

    private static Section section(Long id, Long gradeLevelId) {
        Section section = new Section(
                10L,
                1L,
                gradeLevelId,
                "A",
                "A",
                1
        );
        ReflectionTestUtils.setField(section, "id", id);
        return section;
    }

    private static LocalDate effectiveOn() {
        return LocalDate.of(2025, 12, 31);
    }

    private static String hash(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }

    private static class StubPlanBuilder
            implements StudentProgressionPlanBuilder {
        StudentProgressionPlan plan;
        int calls;

        @Override
        public StudentProgressionPlan buildForPreview(
                long organizationId,
                long targetAcademicYearId,
                com.dawnrise.academic.studentprogression.dto
                        .StudentProgressionPreviewRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentProgressionPlan buildForConfirmation(
                long organizationId,
                long targetAcademicYearId,
                StudentProgressionConfirmationRequest request
        ) {
            calls++;
            return plan;
        }
    }

    private static class OperationRepositoryStub {
        StudentProgressionOperation operation =
                operation(100L, 1, hash('a'), hash('b'));
        StudentProgressionOperation saved;
        int saveAndFlushCalls;

        StudentProgressionOperationRepository repository() {
            return (StudentProgressionOperationRepository)
                    Proxy.newProxyInstance(
                            StudentProgressionOperationRepository.class
                                    .getClassLoader(),
                            new Class<?>[]{
                                    StudentProgressionOperationRepository.class
                            },
                            (proxy, method, args) -> {
                                if (method.getName().equals("hashCode")) {
                                    return System.identityHashCode(proxy);
                                }
                                if (method.getName().equals("equals")) {
                                    return proxy == args[0];
                                }
                                if (method.getName().equals(
                                        "findByIdAndOrganizationIdForUpdate"
                                )) {
                                    return Optional.ofNullable(operation);
                                }
                                if (method.getName().equals("saveAndFlush")) {
                                    saveAndFlushCalls++;
                                    saved = (StudentProgressionOperation)
                                            args[0];
                                    return saved;
                                }
                                throw new UnsupportedOperationException(
                                        method.getName()
                                );
                            }
                    );
        }
    }

    private static class EnrollmentRepositoryStub {
        final List<StudentEnrollment> savedTargetEnrollments =
                new ArrayList<>();
        final List<StudentEnrollment> savedSourceEnrollments =
                new ArrayList<>();
        final AtomicLong targetIds = new AtomicLong(500L);
        int flushCalls;

        StudentEnrollmentRepository repository() {
            return (StudentEnrollmentRepository) Proxy.newProxyInstance(
                    StudentEnrollmentRepository.class.getClassLoader(),
                    new Class<?>[]{StudentEnrollmentRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        if (method.getName().equals("saveAllAndFlush")) {
                            List<StudentEnrollment> values =
                                    iterableToList(args[0]);
                            values.forEach(enrollment ->
                                    ReflectionTestUtils.setField(
                                            enrollment,
                                            "id",
                                            targetIds.getAndIncrement()
                                    ));
                            savedTargetEnrollments.addAll(values);
                            return values;
                        }
                        if (method.getName().equals("saveAll")) {
                            List<StudentEnrollment> values =
                                    iterableToList(args[0]);
                            savedSourceEnrollments.addAll(values);
                            return values;
                        }
                        if (method.getName().equals("flush")) {
                            flushCalls++;
                            return null;
                        }
                        throw new UnsupportedOperationException(
                                method.getName()
                        );
                    }
            );
        }
    }

    private static class ItemRepositoryStub {
        final List<StudentProgressionItem> savedProgressionItems =
                new ArrayList<>();

        StudentProgressionItemRepository repository() {
            return (StudentProgressionItemRepository) Proxy.newProxyInstance(
                    StudentProgressionItemRepository.class.getClassLoader(),
                    new Class<?>[]{StudentProgressionItemRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        if (method.getName().equals("saveAllAndFlush")) {
                            List<StudentProgressionItem> values =
                                    iterableToList(args[0]);
                            savedProgressionItems.addAll(values);
                            return values;
                        }
                        throw new UnsupportedOperationException(
                                method.getName()
                        );
                    }
            );
        }
    }

    private static <T> List<T> iterableToList(Object value) {
        List<T> values = new ArrayList<>();
        ((Iterable<T>) value).forEach(values::add);
        return values;
    }
}
