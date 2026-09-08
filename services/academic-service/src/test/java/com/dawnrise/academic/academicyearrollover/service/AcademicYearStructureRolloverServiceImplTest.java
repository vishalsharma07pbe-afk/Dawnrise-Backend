package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyearrollover.dto.*;
import com.dawnrise.academic.academicyearrollover.entity.AcademicYearStructureRolloverOperation;
import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;
import com.dawnrise.academic.academicyearrollover.exception.InvalidRolloverRequestException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverConflictException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverIdempotencyKeyConflictException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverOperationNotFoundException;
import com.dawnrise.academic.academicyearrollover.repository.AcademicYearStructureRolloverOperationRepository;
import com.dawnrise.academic.academicyearrollover.service.impl.AcademicYearStructureRolloverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicYearStructureRolloverServiceImplTest {

    private StubPlanBuilder planBuilder;
    private StubLifecycleService lifecycleService;
    private StubExecutor executor;
    private OperationRepositoryStub operationRepository;
    private AcademicYearStructureRolloverServiceImpl service;

    @BeforeEach
    void setUp() {
        planBuilder = new StubPlanBuilder();
        lifecycleService = new StubLifecycleService();
        executor = new StubExecutor();
        operationRepository = new OperationRepositoryStub();
        service = new AcademicYearStructureRolloverServiceImpl(
                planBuilder,
                lifecycleService,
                executor,
                operationRepository.repository(),
                JsonMapper.builder().build()
        );
    }

    @Test
    void previewOnlyBuildsAPlanAndReturnsStructuredConflicts() {
        AcademicYearStructureRolloverPreviewResponse response =
                service.preview(10L, 2L, request());

        assertThat(response.canConfirm()).isFalse();
        assertThat(response.conflicts()).extracting(RolloverConflictResponse::type)
                .containsExactly("TARGET_GRADE_LEVEL_CODE_EXISTS");
        assertThat(planBuilder.lastOrganizationId).isEqualTo(10L);
        assertThat(executor.calls).isZero();
        assertThat(lifecycleService.calls).isZero();
    }

    @Test
    void confirmationRequiresValidIdempotencyKeyBeforeRegistration() {
        assertThatThrownBy(() -> service.confirm(
                10L,
                42L,
                2L,
                " ",
                confirmRequest()
        )).isInstanceOf(InvalidRolloverRequestException.class)
                .hasMessage("Idempotency-Key header is required");

        assertThat(lifecycleService.calls).isZero();
        assertThat(executor.calls).isZero();
    }

    @Test
    void exactSuccessfulReplayReturnsStoredResultWithoutExecutingAgain() {
        lifecycleService.registration =
                new RolloverOperationRegistration(100L, true, result(100L));

        RolloverConfirmation confirmation = service.confirm(
                10L,
                42L,
                2L,
                " replay-key ",
                confirmRequest()
        );

        assertThat(confirmation.replay()).isTrue();
        assertThat(confirmation.response().operationId()).isEqualTo(100L);
        assertThat(lifecycleService.lastIdempotencyKey).isEqualTo("replay-key");
        assertThat(executor.calls).isZero();
    }

    @Test
    void sameKeyDifferentRequestReturnsConflictWithoutExecution() {
        lifecycleService.exception =
                new RolloverIdempotencyKeyConflictException(
                        "Idempotency key was already used for a different request"
                );

        assertThatThrownBy(() -> service.confirm(
                10L,
                42L,
                2L,
                "key-1",
                confirmRequest()
        )).isInstanceOf(RolloverIdempotencyKeyConflictException.class);

        assertThat(executor.calls).isZero();
    }

    @Test
    void stalePreviewStatusIsDurablyMarkedAfterExecutorConflict() {
        executor.exception = new RolloverConflictException(
                "Rollover preview is stale",
                RolloverOperationStatus.STALE_PREVIEW,
                "STALE_PREVIEW"
        );

        assertThatThrownBy(() -> service.confirm(
                10L,
                42L,
                2L,
                "key-1",
                confirmRequest()
        )).isInstanceOf(RolloverConflictException.class);

        assertThat(lifecycleService.lastFailureStatus)
                .isEqualTo(RolloverOperationStatus.STALE_PREVIEW);
        assertThat(lifecycleService.lastFailureCode).isEqualTo("STALE_PREVIEW");
        assertThat(lifecycleService.lastFailureMessage)
                .isEqualTo("Rollover preview is stale");
    }

    @Test
    void unexpectedExecutorFailureIsStoredAsSafeFailedStatus() {
        executor.exception = new IllegalStateException("database password leaked");

        assertThatThrownBy(() -> service.confirm(
                10L,
                42L,
                2L,
                "key-1",
                confirmRequest()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(lifecycleService.lastFailureStatus)
                .isEqualTo(RolloverOperationStatus.FAILED);
        assertThat(lifecycleService.lastFailureCode).isEqualTo("ROLLOVER_FAILED");
        assertThat(lifecycleService.lastFailureMessage)
                .isEqualTo("Rollover operation failed");
    }

    @Test
    void operationLookupIsTenantScoped() throws Exception {
        AcademicYearStructureRolloverOperation operation = operation(100L);
        operation.markSucceeded(objectMapper().writeValueAsString(result(100L)));
        operationRepository.found = Optional.of(operation);

        AcademicYearStructureRolloverOperationResponse response =
                service.getOperation(10L, 100L);

        assertThat(response.operationId()).isEqualTo(100L);
        assertThat(operationRepository.lastFindId).isEqualTo(100L);
        assertThat(operationRepository.lastFindOrganizationId).isEqualTo(10L);
    }

    @Test
    void missingOperationDoesNotLeakCrossTenantData() {
        operationRepository.found = Optional.empty();

        assertThatThrownBy(() -> service.getOperation(10L, 100L))
                .isInstanceOf(RolloverOperationNotFoundException.class)
                .hasMessage("Rollover operation not found");
    }

    private static AcademicYearStructureRolloverRequest request() {
        return new AcademicYearStructureRolloverRequest(
                1L, true, false, false, false, List.of(), List.of()
        );
    }

    private static ConfirmAcademicYearStructureRolloverRequest confirmRequest() {
        return new ConfirmAcademicYearStructureRolloverRequest(
                1L, true, false, false, false, List.of(), List.of(), hash('b')
        );
    }

    private static AcademicYearStructureRolloverResultResponse result(Long id) {
        return new AcademicYearStructureRolloverResultResponse(
                id,
                "SUCCEEDED",
                1L,
                2L,
                hash('a'),
                hash('b'),
                new RolloverCountsResponse(1, 0, 0, 0, 1),
                new RolloverMappingsResponse(
                        List.of(new RolloverIdMappingResponse(11L, 21L)),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    private static AcademicYearStructureRolloverOperation operation(Long id) {
        AcademicYearStructureRolloverOperation operation =
                new AcademicYearStructureRolloverOperation(
                        10L, 1L, 2L, "key-1", hash('a'), hash('b'), 42L
                );
        ReflectionTestUtils.setField(operation, "id", id);
        return operation;
    }

    private static String hash(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }

    private static ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    private static class StubPlanBuilder extends RolloverPlanBuilder {
        long lastOrganizationId;

        StubPlanBuilder() {
            super(null, null, null, null, null, new RolloverFingerprintService());
        }

        @Override
        public RolloverPlan build(
                long organizationId,
                long targetAcademicYearId,
                AcademicYearStructureRolloverRequest request
        ) {
            lastOrganizationId = organizationId;
            return new RolloverPlan(
                    new RolloverOptions(
                            request.sourceAcademicYearId(),
                            targetAcademicYearId,
                            true,
                            false,
                            false,
                            false,
                            List.of(),
                            List.of()
                    ),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(new RolloverConflictResponse(
                            "TARGET_GRADE_LEVEL_CODE_EXISTS",
                            "GRADE_LEVEL",
                            11L,
                            "code",
                            "G1",
                            "Target conflict"
                    )),
                    hash('a'),
                    hash('b')
            );
        }
    }

    private static class StubLifecycleService
            implements RolloverOperationLifecycleService {
        int calls;
        String lastIdempotencyKey;
        RolloverOperationRegistration registration =
                new RolloverOperationRegistration(100L, false, null);
        RuntimeException exception;
        RolloverOperationStatus lastFailureStatus;
        String lastFailureCode;
        String lastFailureMessage;

        @Override
        public RolloverOperationRegistration registerOrResolve(
                long organizationId,
                long sourceAcademicYearId,
                long targetAcademicYearId,
                String idempotencyKey,
                String requestHash,
                String previewFingerprint,
                long authenticatedUserId
        ) {
            calls++;
            lastIdempotencyKey = idempotencyKey;
            if (exception != null) {
                throw exception;
            }
            return registration;
        }

        @Override
        public void markTerminalFailure(
                long organizationId,
                long operationId,
                RolloverOperationStatus status,
                String failureCode,
                String failureMessage
        ) {
            lastFailureStatus = status;
            lastFailureCode = failureCode;
            lastFailureMessage = failureMessage;
        }
    }

    private static class StubExecutor implements RolloverExecutor {
        int calls;
        RuntimeException exception;

        @Override
        public AcademicYearStructureRolloverResultResponse execute(
                long organizationId,
                long operationId,
                String submittedPreviewFingerprint,
                AcademicYearStructureRolloverRequest request
        ) {
            calls++;
            if (exception != null) {
                throw exception;
            }
            return result(operationId);
        }
    }

    private static class OperationRepositoryStub {
        Optional<AcademicYearStructureRolloverOperation> found = Optional.empty();
        Long lastFindId;
        Long lastFindOrganizationId;

        AcademicYearStructureRolloverOperationRepository repository() {
            return (AcademicYearStructureRolloverOperationRepository)
                    Proxy.newProxyInstance(
                            AcademicYearStructureRolloverOperationRepository.class
                                    .getClassLoader(),
                            new Class<?>[]{
                                    AcademicYearStructureRolloverOperationRepository.class
                            },
                            (proxy, method, args) -> {
                                if (method.getName().equals("hashCode")) {
                                    return System.identityHashCode(proxy);
                                }
                                if (method.getName().equals("equals")) {
                                    return proxy == args[0];
                                }
                                if (method.getName().equals(
                                        "findByIdAndOrganizationId"
                                )) {
                                    lastFindId = (Long) args[0];
                                    lastFindOrganizationId = (Long) args[1];
                                    return found;
                                }
                                throw new UnsupportedOperationException(
                                        method.getName()
                                );
                            }
                    );
        }
    }
}
