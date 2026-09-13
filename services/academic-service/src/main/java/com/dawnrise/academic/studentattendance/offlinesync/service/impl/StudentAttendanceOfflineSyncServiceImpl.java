package com.dawnrise.academic.studentattendance.offlinesync.service.impl;

import com.dawnrise.academic.common.integration.school.SchoolTimeZoneUnavailableException;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;
import com.dawnrise.academic.studentattendance.offlinesync.entity.StudentAttendanceOfflineSyncOperation;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncFailureCategory;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncStatus;
import com.dawnrise.academic.studentattendance.offlinesync.exception.InvalidStudentAttendanceOfflineSyncException;
import com.dawnrise.academic.studentattendance.offlinesync.exception.StudentAttendanceOfflineSyncConflictException;
import com.dawnrise.academic.studentattendance.offlinesync.exception.StudentAttendanceOfflineSyncUnavailableException;
import com.dawnrise.academic.studentattendance.offlinesync.repository.StudentAttendanceOfflineSyncOperationRepository;
import com.dawnrise.academic.studentattendance.offlinesync.service.StudentAttendanceOfflineSyncFingerprintService;
import com.dawnrise.academic.studentattendance.offlinesync.service.StudentAttendanceOfflineSyncService;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class StudentAttendanceOfflineSyncServiceImpl
        implements StudentAttendanceOfflineSyncService {

    private final StudentAttendanceOfflineSyncOperationRepository repository;
    private final StudentAttendanceOfflineSyncFingerprintService fingerprintService;
    private final StudentAttendanceOfflineSyncProcessor processor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Duration runningTimeout;

    public StudentAttendanceOfflineSyncServiceImpl(
            StudentAttendanceOfflineSyncOperationRepository repository,
            StudentAttendanceOfflineSyncFingerprintService fingerprintService,
            StudentAttendanceOfflineSyncProcessor processor,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            @Value("${student-attendance.offline-sync.running-timeout:PT15M}")
            Duration runningTimeout
    ) {
        this.repository = repository;
        this.fingerprintService = fingerprintService;
        this.processor = processor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.runningTimeout = runningTimeout;
    }

    @Override
    public StudentAttendanceOfflineSyncResponse synchronize(
            long organizationId,
            long actorUserId,
            String idempotencyKey,
            StudentAttendanceOfflineSyncRequest request
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        validateRequest(request);
        String requestHash = fingerprintService.requestHash(
                organizationId,
                actorUserId,
                request
        );
        Registration registration = register(
                organizationId,
                actorUserId,
                normalizedKey,
                requestHash,
                request
        );
        if (registration.replay() != null) {
            return registration.replay().asReplay();
        }

        try {
            return processor.process(
                    organizationId,
                    actorUserId,
                    registration.operationId(),
                    normalizedKey,
                    requestHash,
                    request
            );
        } catch (InvalidStudentAttendanceRecordingException exception) {
            markFailed(organizationId, actorUserId, registration.operationId(),
                    StudentAttendanceOfflineSyncFailureCategory.VALIDATION,
                    exception.getMessage());
            throw exception;
        } catch (StudentAttendanceRecordingConflictException exception) {
            markFailed(organizationId, actorUserId, registration.operationId(),
                    StudentAttendanceOfflineSyncFailureCategory.CONFLICT,
                    exception.getMessage());
            throw exception;
        } catch (StudentAttendanceOfflineSyncConflictException exception) {
            markFailed(organizationId, actorUserId, registration.operationId(),
                    StudentAttendanceOfflineSyncFailureCategory.CONFLICT,
                    exception.getMessage());
            throw exception;
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException exception) {
            markFailed(organizationId, actorUserId, registration.operationId(),
                    StudentAttendanceOfflineSyncFailureCategory.CONFLICT,
                    "Attendance changed while the offline draft was synchronizing");
            throw new StudentAttendanceOfflineSyncConflictException(
                    "Attendance changed while the offline draft was synchronizing"
            );
        } catch (AccessDeniedException exception) {
            markFailed(organizationId, actorUserId, registration.operationId(),
                    StudentAttendanceOfflineSyncFailureCategory.FORBIDDEN,
                    "Access Denied");
            throw exception;
        } catch (SchoolTimeZoneUnavailableException exception) {
            markFailed(organizationId, actorUserId, registration.operationId(),
                    StudentAttendanceOfflineSyncFailureCategory.UNAVAILABLE,
                    "School timezone could not be verified");
            throw exception;
        } catch (RuntimeException exception) {
            String message = "Offline attendance synchronization is temporarily unavailable";
            markFailed(organizationId, actorUserId, registration.operationId(),
                    StudentAttendanceOfflineSyncFailureCategory.UNAVAILABLE,
                    message);
            throw new StudentAttendanceOfflineSyncUnavailableException(
                    message,
                    exception
            );
        }
    }

    private Registration register(
            long organizationId,
            long actorUserId,
            String idempotencyKey,
            String requestHash,
            StudentAttendanceOfflineSyncRequest request
    ) {
        try {
            return transactionTemplate.execute(status ->
                    repository.findByActorKeyForUpdate(
                                    organizationId,
                                    actorUserId,
                                    idempotencyKey
                            )
                            .map(operation -> resolveExisting(operation, requestHash))
                            .orElseGet(() -> {
                                StudentAttendanceOfflineSyncOperation operation =
                                        new StudentAttendanceOfflineSyncOperation(
                                                organizationId,
                                                actorUserId,
                                                request.academicYearId(),
                                                request.gradeLevelId(),
                                                request.sectionId(),
                                                request.attendanceDate(),
                                                idempotencyKey,
                                                requestHash,
                                                request.syncMode()
                                        );
                                repository.saveAndFlush(operation);
                                return new Registration(operation.getId(), null);
                            })
            );
        } catch (DataIntegrityViolationException exception) {
            return transactionTemplate.execute(status -> {
                StudentAttendanceOfflineSyncOperation existing = repository
                        .findByActorKeyForUpdate(
                                organizationId,
                                actorUserId,
                                idempotencyKey
                        )
                        .orElseThrow(() -> new StudentAttendanceOfflineSyncConflictException(
                                "Idempotency key could not be resolved"
                        ));
                return resolveExisting(existing, requestHash);
            });
        }
    }

    private Registration resolveExisting(
            StudentAttendanceOfflineSyncOperation operation,
            String requestHash
    ) {
        if (!Objects.equals(operation.getRequestHash(), requestHash)) {
            throw new StudentAttendanceOfflineSyncConflictException(
                    "Idempotency key was already used for a different offline attendance request"
            );
        }
        if (operation.getStatus() == StudentAttendanceOfflineSyncStatus.SUCCEEDED) {
            return new Registration(operation.getId(), readResult(operation.getResultJson()));
        }
        if (operation.getStatus() == StudentAttendanceOfflineSyncStatus.RUNNING) {
            if (operation.getStartedAt() != null
                    && operation.getStartedAt().plus(runningTimeout)
                    .isBefore(OffsetDateTime.now())) {
                operation.restartAfterInterruption();
                repository.saveAndFlush(operation);
                return new Registration(operation.getId(), null);
            }
            throw new StudentAttendanceOfflineSyncConflictException(
                    "Offline attendance synchronization is already running"
            );
        }
        replayFailure(operation);
        throw new IllegalStateException("Unreachable offline sync state");
    }

    private void replayFailure(StudentAttendanceOfflineSyncOperation operation) {
        String message = operation.getFailureMessage();
        switch (operation.getFailureCategory()) {
            case VALIDATION -> throw new InvalidStudentAttendanceRecordingException(message);
            case CONFLICT -> throw new StudentAttendanceRecordingConflictException(message);
            case FORBIDDEN -> throw new AccessDeniedException("Access Denied");
            case UNAVAILABLE -> throw new StudentAttendanceOfflineSyncUnavailableException(message);
        }
    }

    private void markFailed(
            long organizationId,
            long actorUserId,
            long operationId,
            StudentAttendanceOfflineSyncFailureCategory category,
            String message
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            StudentAttendanceOfflineSyncOperation operation = operationForUpdate(
                    organizationId, actorUserId, operationId);
            if (operation.getStatus() == StudentAttendanceOfflineSyncStatus.RUNNING) {
                operation.fail(category, safeMessage(message));
                repository.saveAndFlush(operation);
            }
        });
    }

    private StudentAttendanceOfflineSyncOperation operationForUpdate(
            long organizationId,
            long actorUserId,
            long operationId
    ) {
        return repository.findForUpdate(operationId, organizationId, actorUserId)
                .orElseThrow(() -> new StudentAttendanceOfflineSyncConflictException(
                        "Offline attendance synchronization operation was not found"
                ));
    }

    private StudentAttendanceOfflineSyncResponse readResult(String json) {
        try {
            return objectMapper.readValue(json, StudentAttendanceOfflineSyncResponse.class);
        } catch (Exception exception) {
            throw new StudentAttendanceOfflineSyncConflictException(
                    "Stored offline attendance result could not be read"
            );
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "Idempotency-Key header is required"
            );
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "Idempotency-Key header cannot exceed 128 characters"
            );
        }
        return normalized;
    }

    private void validateRequest(StudentAttendanceOfflineSyncRequest request) {
        if (request == null || request.records() == null || request.records().isEmpty()) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "Offline attendance records are required"
            );
        }
        if (request.records().size() > 100) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "At most 100 offline attendance records can be synchronized at once"
            );
        }
        if (request.academicYearId() == null || request.academicYearId() <= 0
                || request.gradeLevelId() == null || request.gradeLevelId() <= 0
                || request.sectionId() == null || request.sectionId() <= 0
                || request.attendanceDate() == null
                || request.syncMode() == null) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "Offline attendance context is incomplete"
            );
        }
        if (request.baseSessionVersion() != null
                && request.baseSessionVersion() < 0) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "Base attendance session version cannot be negative"
            );
        }
        if (request.expectedRosterEnrollmentIds() == null
                || request.expectedRosterEnrollmentIds().isEmpty()
                || request.expectedRosterEnrollmentIds().size() > 500
                || request.expectedRosterEnrollmentIds().stream()
                .anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(request.expectedRosterEnrollmentIds()).size()
                != request.expectedRosterEnrollmentIds().size()) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "Expected roster must contain at most 500 unique positive enrollment IDs"
            );
        }
        Set<Long> recordEnrollmentIds = new HashSet<>();
        for (var record : request.records()) {
            if (record == null
                    || record.studentEnrollmentId() == null
                    || record.studentEnrollmentId() <= 0
                    || record.recordedStatus() == null
                    || (record.expectedRecordVersion() != null
                    && record.expectedRecordVersion() < 0)) {
                throw new InvalidStudentAttendanceOfflineSyncException(
                        "Each offline attendance record is incomplete"
                );
            }
            if (!recordEnrollmentIds.add(record.studentEnrollmentId())) {
                throw new InvalidStudentAttendanceOfflineSyncException(
                        "Duplicate student enrollment IDs are not allowed"
                );
            }
        }
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank()
                ? "Offline attendance synchronization failed"
                : message;
    }

    private record Registration(
            Long operationId,
            StudentAttendanceOfflineSyncResponse replay
    ) {
    }
}
