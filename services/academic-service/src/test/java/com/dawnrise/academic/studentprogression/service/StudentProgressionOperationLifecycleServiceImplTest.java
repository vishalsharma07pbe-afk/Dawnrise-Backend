package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionCountsResponse;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionOperation;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.exception.InvalidStudentProgressionException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionIdempotencyKeyConflictException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionOperationAlreadyRunningException;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionOperationRepository;
import com.dawnrise.academic.studentprogression.service.impl.StudentProgressionOperationLifecycleServiceImpl;
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

class StudentProgressionOperationLifecycleServiceImplTest {

    private OperationRepositoryStub repository;
    private StudentProgressionOperationLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new OperationRepositoryStub();
        service = new StudentProgressionOperationLifecycleServiceImpl(
                repository.repository(),
                JsonMapper.builder().build(),
                new TransactionTemplate(new CountingTransactionManager()),
                Duration.ofMinutes(15)
        );
    }

    @Test
    void newOperationIsRegisteredRunningWithNormalizedIdempotencyKey() {
        StudentProgressionOperationRegistration registration =
                service.registerOrResolve(
                        10L,
                        1L,
                        2L,
                        "Annual promotion",
                        " key-1 ",
                        hash('a'),
                        hash('b'),
                        42L,
                        2
                );

        assertThat(registration.operationId()).isEqualTo(100L);
        assertThat(registration.replay()).isFalse();
        assertThat(repository.saved.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.RUNNING);
        assertThat(repository.saved.getOrganizationId()).isEqualTo(10L);
        assertThat(repository.saved.getIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void blankIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> service.registerOrResolve(
                10L,
                1L,
                2L,
                "Annual promotion",
                " ",
                hash('a'),
                hash('b'),
                42L,
                1
        )).isInstanceOf(InvalidStudentProgressionException.class)
                .hasMessage("Idempotency-Key header is required");
    }

    @Test
    void successfulOperationReplaysStoredResultExactly() throws Exception {
        StudentProgressionOperation operation = operation(100L);
        operation.markSucceeded(JsonMapper.builder().build()
                .writeValueAsString(result(100L)));
        repository.existing = Optional.of(operation);

        StudentProgressionOperationRegistration registration =
                service.registerOrResolve(
                        10L,
                        1L,
                        2L,
                        "Annual promotion",
                        "key-1",
                        hash('a'),
                        hash('b'),
                        42L,
                        1
                );

        assertThat(registration.replay()).isTrue();
        assertThat(registration.replayResult()).isEqualTo(result(100L));
    }

    @Test
    void sameKeyWithDifferentRequestReturnsConflict() {
        repository.existing = Optional.of(operation(100L));

        assertThatThrownBy(() -> service.registerOrResolve(
                10L,
                1L,
                2L,
                "Annual promotion",
                "key-1",
                hash('c'),
                hash('b'),
                42L,
                1
        )).isInstanceOf(
                StudentProgressionIdempotencyKeyConflictException.class
        ).hasMessage(
                "Idempotency key was already used for a different request"
        );
    }

    @Test
    void concurrentUniqueInsertRaceResolvesExistingOperation()
            throws Exception {
        repository.failFirstSaveWithUniqueViolation = true;
        StudentProgressionOperation existing = operation(101L);
        existing.markSucceeded(JsonMapper.builder().build()
                .writeValueAsString(result(101L)));
        repository.existingAfterFailedInsert = Optional.of(existing);

        StudentProgressionOperationRegistration registration =
                service.registerOrResolve(
                        10L,
                        1L,
                        2L,
                        "Annual promotion",
                        "key-1",
                        hash('a'),
                        hash('b'),
                        42L,
                        1
                );

        assertThat(registration.replay()).isTrue();
        assertThat(registration.operationId()).isEqualTo(101L);
        assertThat(repository.findByKeyCalls).isEqualTo(2);
    }

    @Test
    void nonTimedOutRunningOperationReturnsAlreadyRunning() {
        StudentProgressionOperation running = operation(100L);
        ReflectionTestUtils.setField(
                running,
                "startedAt",
                OffsetDateTime.now().minusMinutes(5)
        );
        repository.existing = Optional.of(running);

        assertThatThrownBy(() -> service.registerOrResolve(
                10L,
                1L,
                2L,
                "Annual promotion",
                "key-1",
                hash('a'),
                hash('b'),
                42L,
                1
        )).isInstanceOf(
                StudentProgressionOperationAlreadyRunningException.class
        );
    }

    @Test
    void staleRunningOperationIsMarkedFailedAndReturnedForExecutorRejection() {
        StudentProgressionOperation running = operation(100L);
        ReflectionTestUtils.setField(
                running,
                "startedAt",
                OffsetDateTime.now().minusMinutes(16)
        );
        repository.existing = Optional.of(running);

        StudentProgressionOperationRegistration registration =
                service.registerOrResolve(
                        10L,
                        1L,
                        2L,
                        "Annual promotion",
                        "key-1",
                        hash('a'),
                        hash('b'),
                        42L,
                        1
                );

        assertThat(registration.operationId()).isEqualTo(100L);
        assertThat(registration.replay()).isFalse();
        assertThat(repository.saved.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.FAILED);
        assertThat(repository.saved.getFailureCode())
                .isEqualTo("OPERATION_INTERRUPTED");
    }

    @Test
    void failedPreviousOperationReturnsConflictWithOriginalFailureCode() {
        StudentProgressionOperation failed = operation(100L);
        failed.markFailed("TARGET_CONFLICT", "Target conflict");
        repository.existing = Optional.of(failed);

        assertThatThrownBy(() -> service.registerOrResolve(
                10L,
                1L,
                2L,
                "Annual promotion",
                "key-1",
                hash('a'),
                hash('b'),
                42L,
                1
        )).isInstanceOf(StudentProgressionConflictException.class)
                .hasMessage(
                        "Previous progression attempt with this idempotency key did not succeed"
                )
                .extracting("failureCode")
                .isEqualTo("TARGET_CONFLICT");
    }

    @Test
    void markTerminalFailureUsesLockedTenantScopedLookup() {
        repository.locked = Optional.of(operation(100L));

        service.markTerminalFailure(
                10L,
                100L,
                StudentProgressionOperationStatus.CONFLICTED,
                "TARGET_CONFLICT",
                "Target conflict"
        );

        assertThat(repository.lastLockedId).isEqualTo(100L);
        assertThat(repository.lastLockedOrganizationId).isEqualTo(10L);
        assertThat(repository.saved.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.CONFLICTED);
        assertThat(repository.saved.getFailureMessage())
                .isEqualTo("Target conflict");
    }

    private static StudentProgressionConfirmationResponse result(Long id) {
        return new StudentProgressionConfirmationResponse(
                id,
                StudentProgressionOperationStatus.SUCCEEDED,
                1L,
                2L,
                "Annual promotion",
                hash('a'),
                hash('b'),
                new StudentProgressionCountsResponse(1, 1, 0, 0, 0, 0),
                List.of()
        );
    }

    private static StudentProgressionOperation operation(Long id) {
        StudentProgressionOperation operation =
                new StudentProgressionOperation(
                        10L,
                        1L,
                        2L,
                        "Annual promotion",
                        "key-1",
                        hash('a'),
                        hash('b'),
                        42L,
                        1
                );
        operation.markRunning();
        ReflectionTestUtils.setField(operation, "id", id);
        return operation;
    }

    private static String hash(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }

    private static class CountingTransactionManager
            implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(
                TransactionDefinition definition
        ) {
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
        Optional<StudentProgressionOperation> existing =
                Optional.empty();
        Optional<StudentProgressionOperation> existingAfterFailedInsert =
                Optional.empty();
        Optional<StudentProgressionOperation> locked = Optional.empty();
        StudentProgressionOperation saved;
        boolean failFirstSaveWithUniqueViolation;
        int findByKeyCalls;
        Long lastLockedId;
        Long lastLockedOrganizationId;

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
                                    saved = (StudentProgressionOperation)
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
