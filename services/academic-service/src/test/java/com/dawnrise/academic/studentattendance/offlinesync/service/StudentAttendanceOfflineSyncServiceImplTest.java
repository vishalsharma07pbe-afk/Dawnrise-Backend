package com.dawnrise.academic.studentattendance.offlinesync.service;

import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRecordRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;
import com.dawnrise.academic.studentattendance.offlinesync.entity.StudentAttendanceOfflineSyncOperation;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncMode;
import com.dawnrise.academic.studentattendance.offlinesync.exception.InvalidStudentAttendanceOfflineSyncException;
import com.dawnrise.academic.studentattendance.offlinesync.exception.StudentAttendanceOfflineSyncConflictException;
import com.dawnrise.academic.studentattendance.offlinesync.repository.StudentAttendanceOfflineSyncOperationRepository;
import com.dawnrise.academic.studentattendance.offlinesync.service.impl.StudentAttendanceOfflineSyncProcessor;
import com.dawnrise.academic.studentattendance.offlinesync.service.impl.StudentAttendanceOfflineSyncServiceImpl;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudentAttendanceOfflineSyncServiceImplTest {

    private StudentAttendanceOfflineSyncOperationRepository repository;
    private StudentAttendanceOfflineSyncProcessor processor;
    private StudentAttendanceOfflineSyncFingerprintService fingerprint;
    private ObjectMapper objectMapper;
    private StudentAttendanceOfflineSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(StudentAttendanceOfflineSyncOperationRepository.class);
        processor = mock(StudentAttendanceOfflineSyncProcessor.class);
        fingerprint = new StudentAttendanceOfflineSyncFingerprintService();
        objectMapper = new ObjectMapper();
        service = new StudentAttendanceOfflineSyncServiceImpl(
                repository,
                fingerprint,
                processor,
                transactionTemplate(),
                objectMapper,
                Duration.ofMinutes(15)
        );
    }

    @Test
    void firstRequestRegistersAndProcessesOperation() throws Exception {
        StudentAttendanceOfflineSyncRequest request = request(AttendanceStatus.PRESENT);
        when(repository.findByActorKeyForUpdate(
                7L, 11L, "offline-1"
        )).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            StudentAttendanceOfflineSyncOperation operation = invocation.getArgument(0);
            setId(operation, 41L);
            return operation;
        });
        StudentAttendanceOfflineSyncResponse expected = new StudentAttendanceOfflineSyncResponse(
                41L, "offline-1", fingerprint.requestHash(7L, 11L, request),
                false, null, List.of()
        );
        when(processor.process(7L, 11L, 41L, "offline-1",
                expected.requestHash(), request)).thenReturn(expected);

        StudentAttendanceOfflineSyncResponse result = service.synchronize(
                7L, 11L, " offline-1 ", request
        );

        assertThat(result).isEqualTo(expected);
        verify(processor).process(7L, 11L, 41L, "offline-1",
                expected.requestHash(), request);
    }

    @Test
    void succeededRequestReplaysStoredResultWithoutProcessing() throws Exception {
        StudentAttendanceOfflineSyncRequest request = request(AttendanceStatus.PRESENT);
        String hash = fingerprint.requestHash(7L, 11L, request);
        StudentAttendanceOfflineSyncResponse stored = new StudentAttendanceOfflineSyncResponse(
                41L, "offline-1", hash, false, null, List.of()
        );
        StudentAttendanceOfflineSyncOperation operation = operation(hash);
        setId(operation, 41L);
        operation.succeed(21L, objectMapper.writeValueAsString(stored));
        when(repository.findByActorKeyForUpdate(
                7L, 11L, "offline-1"
        )).thenReturn(Optional.of(operation));

        StudentAttendanceOfflineSyncResponse replay = service.synchronize(
                7L, 11L, "offline-1", request
        );

        assertThat(replay.replay()).isTrue();
        assertThat(replay.operationId()).isEqualTo(41L);
        verifyNoInteractions(processor);
    }

    @Test
    void sameKeyWithDifferentPayloadIsConflict() {
        StudentAttendanceOfflineSyncRequest original = request(AttendanceStatus.PRESENT);
        StudentAttendanceOfflineSyncOperation operation = operation(
                fingerprint.requestHash(7L, 11L, original)
        );
        when(repository.findByActorKeyForUpdate(
                7L, 11L, "offline-1"
        )).thenReturn(Optional.of(operation));

        assertThatThrownBy(() -> service.synchronize(
                7L, 11L, "offline-1", request(AttendanceStatus.ABSENT)
        )).isInstanceOf(StudentAttendanceOfflineSyncConflictException.class)
                .hasMessageContaining("different offline attendance request");
        verifyNoInteractions(processor);
    }

    @Test
    void missingKeyAndEmptyRecordsAreRejectedBeforePersistence() {
        assertThatThrownBy(() -> service.synchronize(7L, 11L, null, request(AttendanceStatus.PRESENT)))
                .isInstanceOf(InvalidStudentAttendanceOfflineSyncException.class);
        StudentAttendanceOfflineSyncRequest empty = new StudentAttendanceOfflineSyncRequest(
                3L, 3L, 3L, LocalDate.of(2026, 9, 11), 0L,
                StudentAttendanceOfflineSyncMode.PARTIAL, List.of(3L), List.of()
        );
        assertThatThrownBy(() -> service.synchronize(7L, 11L, "offline-2", empty))
                .isInstanceOf(InvalidStudentAttendanceOfflineSyncException.class);
        verifyNoInteractions(repository, processor);
    }

    private StudentAttendanceOfflineSyncOperation operation(String hash) {
        return new StudentAttendanceOfflineSyncOperation(
                7L, 11L, 3L, 3L, 3L,
                LocalDate.of(2026, 9, 11), "offline-1", hash,
                StudentAttendanceOfflineSyncMode.PARTIAL
        );
    }

    private StudentAttendanceOfflineSyncRequest request(AttendanceStatus status) {
        return new StudentAttendanceOfflineSyncRequest(
                3L, 3L, 3L, LocalDate.of(2026, 9, 11), 0L,
                StudentAttendanceOfflineSyncMode.PARTIAL,
                List.of(3L),
                List.of(new StudentAttendanceOfflineSyncRecordRequest(
                        3L, 0L, status, "offline"
                ))
        );
    }

    private void setId(StudentAttendanceOfflineSyncOperation operation, Long id)
            throws Exception {
        Field field = StudentAttendanceOfflineSyncOperation.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(operation, id);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
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
        });
    }
}
