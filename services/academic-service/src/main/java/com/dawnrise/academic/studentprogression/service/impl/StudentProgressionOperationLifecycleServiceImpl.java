package com.dawnrise.academic.studentprogression.service.impl;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionOperation;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.exception.InvalidStudentProgressionException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionIdempotencyKeyConflictException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionOperationAlreadyRunningException;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionOperationRepository;
import com.dawnrise.academic.studentprogression.service.StudentProgressionOperationLifecycleService;
import com.dawnrise.academic.studentprogression.service.StudentProgressionOperationRegistration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class StudentProgressionOperationLifecycleServiceImpl
        implements StudentProgressionOperationLifecycleService {

    private final StudentProgressionOperationRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Duration runningTimeout;

    public StudentProgressionOperationLifecycleServiceImpl(
            StudentProgressionOperationRepository repository,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Value("${student-progression.running-timeout:PT15M}")
            Duration runningTimeout
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.runningTimeout = runningTimeout;
    }

    @Override
    public StudentProgressionOperationRegistration registerOrResolve(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            String batchLabel,
            String idempotencyKey,
            String requestHash,
            String previewFingerprint,
            long authenticatedUserId,
            int totalItems
    ) {
        String normalizedKey = normalizeIdempotencyKey(
                idempotencyKey
        );

        try {
            return transactionTemplate.execute(status ->
                    registerOrResolveInTransaction(
                            organizationId,
                            sourceAcademicYearId,
                            targetAcademicYearId,
                            batchLabel,
                            normalizedKey,
                            requestHash,
                            previewFingerprint,
                            authenticatedUserId,
                            totalItems
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            return transactionTemplate.execute(status ->
                    resolveExistingInTransaction(
                            organizationId,
                            normalizedKey,
                            requestHash
                    )
            );
        }
    }

    private StudentProgressionOperationRegistration
    registerOrResolveInTransaction(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            String batchLabel,
            String idempotencyKey,
            String requestHash,
            String previewFingerprint,
            long authenticatedUserId,
            int totalItems
    ) {
        return repository
                .findByOrganizationIdAndIdempotencyKey(
                        organizationId,
                        idempotencyKey
                )
                .map(operation ->
                        resolveExisting(
                                operation,
                                requestHash
                        )
                )
                .orElseGet(() -> {
                    StudentProgressionOperation operation =
                            new StudentProgressionOperation(
                                    organizationId,
                                    sourceAcademicYearId,
                                    targetAcademicYearId,
                                    batchLabel,
                                    idempotencyKey,
                                    requestHash,
                                    previewFingerprint,
                                    authenticatedUserId,
                                    totalItems
                            );

                    operation.markRunning();

                    StudentProgressionOperation saved =
                            repository.saveAndFlush(operation);

                    return new StudentProgressionOperationRegistration(
                            saved.getId(),
                            false,
                            null
                    );
                });
    }

    private StudentProgressionOperationRegistration
    resolveExistingInTransaction(
            long organizationId,
            String idempotencyKey,
            String requestHash
    ) {
        StudentProgressionOperation operation = repository
                .findByOrganizationIdAndIdempotencyKey(
                        organizationId,
                        idempotencyKey
                )
                .orElseThrow(() ->
                        new StudentProgressionIdempotencyKeyConflictException(
                                "Idempotency key could not be resolved"
                        )
                );

        return resolveExisting(operation, requestHash);
    }

    private StudentProgressionOperationRegistration resolveExisting(
            StudentProgressionOperation operation,
            String requestHash
    ) {
        if (!Objects.equals(
                operation.getRequestHash(),
                requestHash
        )) {
            throw new StudentProgressionIdempotencyKeyConflictException(
                    "Idempotency key was already used for a different request"
            );
        }

        if (operation.getStatus()
                == StudentProgressionOperationStatus.SUCCEEDED) {
            return new StudentProgressionOperationRegistration(
                    operation.getId(),
                    true,
                    readResult(operation.getResultJson())
            );
        }

        if (operation.getStatus()
                == StudentProgressionOperationStatus.RUNNING) {
            return handleRunningOperation(operation);
        }

        throw new StudentProgressionConflictException(
                "Previous progression attempt with this idempotency key did not succeed",
                operation.getStatus(),
                operation.getFailureCode() == null
                        ? "PREVIOUS_ATTEMPT_FAILED"
                        : operation.getFailureCode()
        );
    }

    private StudentProgressionOperationRegistration
    handleRunningOperation(
            StudentProgressionOperation operation
    ) {
        boolean timedOut =
                operation.getStartedAt() != null
                        && operation.getStartedAt()
                        .plus(runningTimeout)
                        .isBefore(OffsetDateTime.now());

        if (!timedOut) {
            throw new StudentProgressionOperationAlreadyRunningException(
                    "Student progression operation is already running"
            );
        }

        operation.markFailed(
                "OPERATION_INTERRUPTED",
                "Previous student progression operation was interrupted"
        );

        repository.saveAndFlush(operation);

        /*
         * Return normally so the transaction commits the FAILED status.
         * The executor will reject the operation because it is no longer
         * RUNNING.
         */
        return new StudentProgressionOperationRegistration(
                operation.getId(),
                false,
                null
        );
    }

    @Override
    public void markTerminalFailure(
            long organizationId,
            long operationId,
            StudentProgressionOperationStatus status,
            String failureCode,
            String failureMessage
    ) {
        transactionTemplate.executeWithoutResult(transactionStatus ->
                repository.findByIdAndOrganizationIdForUpdate(
                                operationId,
                                organizationId
                        )
                        .ifPresent(operation -> {
                            if (operation.getStatus()
                                    != StudentProgressionOperationStatus.RUNNING) {
                                return;
                            }

                            markTerminal(
                                    operation,
                                    status,
                                    failureCode,
                                    failureMessage
                            );

                            repository.saveAndFlush(operation);
                        })
        );
    }

    private void markTerminal(
            StudentProgressionOperation operation,
            StudentProgressionOperationStatus status,
            String failureCode,
            String failureMessage
    ) {
        switch (status) {
            case FAILED -> operation.markFailed(
                    failureCode,
                    failureMessage
            );

            case STALE_PREVIEW -> operation.markStalePreview(
                    failureCode,
                    failureMessage
            );

            case CONFLICTED -> operation.markConflicted(
                    failureCode,
                    failureMessage
            );

            default -> throw new InvalidStudentProgressionException(
                    "Operation cannot be terminated with status "
                            + status
            );
        }
    }

    private StudentProgressionConfirmationResponse readResult(
            String resultJson
    ) {
        try {
            return objectMapper.readValue(
                    resultJson,
                    StudentProgressionConfirmationResponse.class
            );
        } catch (Exception exception) {
            throw new StudentProgressionConflictException(
                    "Stored student progression result could not be read",
                    StudentProgressionOperationStatus.FAILED,
                    "RESULT_UNAVAILABLE"
            );
        }
    }

    private String normalizeIdempotencyKey(
            String idempotencyKey
    ) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new InvalidStudentProgressionException(
                    "Idempotency-Key header is required"
            );
        }

        String normalized = idempotencyKey.trim();

        if (normalized.length() > 128) {
            throw new InvalidStudentProgressionException(
                    "Idempotency-Key header cannot exceed 128 characters"
            );
        }

        return normalized;
    }
}