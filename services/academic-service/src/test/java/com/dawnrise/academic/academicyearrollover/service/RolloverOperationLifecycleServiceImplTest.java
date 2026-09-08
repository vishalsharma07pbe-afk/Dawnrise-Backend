package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyearrollover.dto.*;
import com.dawnrise.academic.academicyearrollover.entity.AcademicYearStructureRolloverOperation;
import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;
import com.dawnrise.academic.academicyearrollover.exception.RolloverConflictException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverIdempotencyKeyConflictException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverOperationAlreadyRunningException;
import com.dawnrise.academic.academicyearrollover.repository.AcademicYearStructureRolloverOperationRepository;
import com.dawnrise.academic.academicyearrollover.service.impl.RolloverOperationLifecycleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolloverOperationLifecycleServiceImplTest {

    private OperationRepositoryStub repository;
    private RolloverOperationLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new OperationRepositoryStub();
        service = new RolloverOperationLifecycleServiceImpl(
                repository.repository(),
                JsonMapper.builder().build(),
                new TransactionTemplate(new CountingTransactionManager()),
                Duration.ofMinutes(15)
        );
    }

    @Test
    void newOperationIsRegisteredRunning() {
        RolloverOperationRegistration registration = service.registerOrResolve(
                10L, 1L, 2L, "key-1", hash('a'), hash('b'), 42L
        );

        assertThat(registration.operationId()).isEqualTo(100L);
        assertThat(registration.replay()).isFalse();
        assertThat(repository.saved.getStatus())
                .isEqualTo(RolloverOperationStatus.RUNNING);
        assertThat(repository.saved.getOrganizationId()).isEqualTo(10L);
    }

    @Test
    void successfulOperationReplaysStoredResultExactly() throws Exception {
        AcademicYearStructureRolloverOperation operation = operation(100L);
        operation.markSucceeded(JsonMapper.builder().build()
                .writeValueAsString(result(100L)));
        repository.existing = Optional.of(operation);

        RolloverOperationRegistration registration = service.registerOrResolve(
                10L, 1L, 2L, "key-1", hash('a'), hash('b'), 42L
        );

        assertThat(registration.replay()).isTrue();
        assertThat(registration.replayResult()).isEqualTo(result(100L));
    }

    @Test
    void sameKeyWithDifferentRequestReturnsConflict() {
        repository.existing = Optional.of(operation(100L));

        assertThatThrownBy(() -> service.registerOrResolve(
                10L, 1L, 2L, "key-1", hash('c'), hash('b'), 42L
        )).isInstanceOf(RolloverIdempotencyKeyConflictException.class)
                .hasMessage("Idempotency key was already used for a different request");
    }

    @Test
    void concurrentUniqueInsertRaceResolvesExistingOperation() throws Exception {
        repository.failFirstSaveWithUniqueViolation = true;
        AcademicYearStructureRolloverOperation existing = operation(101L);
        existing.markSucceeded(JsonMapper.builder().build()
                .writeValueAsString(result(101L)));
        repository.existingAfterFailedInsert = Optional.of(existing);

        RolloverOperationRegistration registration = service.registerOrResolve(
                10L, 1L, 2L, "key-1", hash('a'), hash('b'), 42L
        );

        assertThat(registration.replay()).isTrue();
        assertThat(registration.operationId()).isEqualTo(101L);
        assertThat(repository.findByKeyCalls).isEqualTo(2);
    }

    @Test
    void nonTimedOutRunningOperationReturnsAlreadyRunning() {
        AcademicYearStructureRolloverOperation running = operation(100L);
        running.markRunning();
        ReflectionTestUtils.setField(
                running,
                "startedAt",
                OffsetDateTime.now().minusMinutes(5)
        );
        repository.existing = Optional.of(running);

        assertThatThrownBy(() -> service.registerOrResolve(
                10L, 1L, 2L, "key-1", hash('a'), hash('b'), 42L
        )).isInstanceOf(RolloverOperationAlreadyRunningException.class);
    }

    @Test
    void staleRunningOperationIsMarkedFailedWithTimeoutPolicy() {
        AcademicYearStructureRolloverOperation running = operation(100L);
        running.markRunning();
        ReflectionTestUtils.setField(
                running,
                "startedAt",
                OffsetDateTime.now().minusMinutes(16)
        );
        repository.existing = Optional.of(running);

        assertThatThrownBy(() -> service.registerOrResolve(
                10L, 1L, 2L, "key-1", hash('a'), hash('b'), 42L
        )).isInstanceOf(RolloverConflictException.class)
                .hasMessage("Previous rollover operation was interrupted; use a new preview and idempotency key");

        assertThat(repository.saved.getStatus())
                .isEqualTo(RolloverOperationStatus.FAILED);
        assertThat(repository.saved.getFailureCode())
                .isEqualTo("OPERATION_INTERRUPTED");
    }

    @Test
    void markTerminalFailureUsesLockedTenantScopedLookup() {
        repository.locked = Optional.of(operation(100L));

        service.markTerminalFailure(
                10L,
                100L,
                RolloverOperationStatus.CONFLICTED,
                "TARGET_CONFLICT",
                "Target conflict"
        );

        assertThat(repository.lastLockedId).isEqualTo(100L);
        assertThat(repository.lastLockedOrganizationId).isEqualTo(10L);
        assertThat(repository.saved.getStatus())
                .isEqualTo(RolloverOperationStatus.CONFLICTED);
        assertThat(repository.saved.getFailureMessage())
                .isEqualTo("Target conflict");
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

    private static class CountingTransactionManager
            implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    private static class OperationRepositoryStub {
        Optional<AcademicYearStructureRolloverOperation> existing =
                Optional.empty();
        Optional<AcademicYearStructureRolloverOperation> existingAfterFailedInsert =
                Optional.empty();
        Optional<AcademicYearStructureRolloverOperation> locked = Optional.empty();
        AcademicYearStructureRolloverOperation saved;
        boolean failFirstSaveWithUniqueViolation;
        int findByKeyCalls;
        Long lastLockedId;
        Long lastLockedOrganizationId;

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
                                        "findByOrganizationIdAndIdempotencyKey"
                                )) {
                                    findByKeyCalls++;
                                    if (findByKeyCalls > 1) {
                                        return existingAfterFailedInsert;
                                    }
                                    return existing;
                                }
                                if (method.getName().equals("saveAndFlush")) {
                                    if (failFirstSaveWithUniqueViolation) {
                                        failFirstSaveWithUniqueViolation = false;
                                        throw new DataIntegrityViolationException(
                                                "duplicate key"
                                        );
                                    }
                                    saved = (AcademicYearStructureRolloverOperation)
                                            args[0];
                                    if (saved.getId() == null) {
                                        ReflectionTestUtils.setField(
                                                saved,
                                                "id",
                                                100L
                                        );
                                    }
                                    return saved;
                                }
                                if (method.getName().equals(
                                        "findByIdAndOrganizationIdForUpdate"
                                )) {
                                    lastLockedId = (Long) args[0];
                                    lastLockedOrganizationId = (Long) args[1];
                                    return locked;
                                }
                                throw new UnsupportedOperationException(
                                        method.getName()
                                );
                            }
                    );
        }
    }
}
