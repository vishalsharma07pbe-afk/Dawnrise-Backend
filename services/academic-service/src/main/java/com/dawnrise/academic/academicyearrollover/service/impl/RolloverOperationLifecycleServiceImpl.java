package com.dawnrise.academic.academicyearrollover.service.impl;

import com.dawnrise.academic.academicyearrollover.dto.AcademicYearStructureRolloverResultResponse;
import com.dawnrise.academic.academicyearrollover.entity.AcademicYearStructureRolloverOperation;
import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;
import com.dawnrise.academic.academicyearrollover.exception.RolloverConflictException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverIdempotencyKeyConflictException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverOperationAlreadyRunningException;
import com.dawnrise.academic.academicyearrollover.repository.AcademicYearStructureRolloverOperationRepository;
import com.dawnrise.academic.academicyearrollover.service.RolloverOperationLifecycleService;
import com.dawnrise.academic.academicyearrollover.service.RolloverOperationRegistration;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class RolloverOperationLifecycleServiceImpl
        implements RolloverOperationLifecycleService {

    private final AcademicYearStructureRolloverOperationRepository repository;
    private final ObjectMapper objectMapper;
    private final Duration runningTimeout;
    private final TransactionTemplate transactionTemplate;

    public RolloverOperationLifecycleServiceImpl(
            AcademicYearStructureRolloverOperationRepository repository,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Value("${academic-year-structure-rollover.running-timeout:PT15M}")
            Duration runningTimeout
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.runningTimeout = runningTimeout;
    }

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
        try {
            return transactionTemplate.execute(status -> registerOrResolveInTransaction(
                    organizationId,
                    sourceAcademicYearId,
                    targetAcademicYearId,
                    idempotencyKey,
                    requestHash,
                    previewFingerprint,
                    authenticatedUserId
            ));
        } catch (DataIntegrityViolationException exception) {
            return transactionTemplate.execute(status -> resolveExistingInTransaction(
                    organizationId,
                    idempotencyKey,
                    requestHash
            ));
        }
    }

    private RolloverOperationRegistration registerOrResolveInTransaction(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            String idempotencyKey,
            String requestHash,
            String previewFingerprint,
            long authenticatedUserId
    ) {
        return repository
                .findByOrganizationIdAndIdempotencyKey(
                        organizationId,
                        idempotencyKey
                )
                .map(operation -> resolveExisting(operation, requestHash))
                .orElseGet(() -> {
                    AcademicYearStructureRolloverOperation operation =
                            new AcademicYearStructureRolloverOperation(
                                    organizationId,
                                    sourceAcademicYearId,
                                    targetAcademicYearId,
                                    idempotencyKey,
                                    requestHash,
                                    previewFingerprint,
                                    authenticatedUserId
                            );
                    operation.markRunning();
                    AcademicYearStructureRolloverOperation saved =
                            repository.saveAndFlush(operation);
                    return new RolloverOperationRegistration(
                            saved.getId(),
                            false,
                            null
                    );
                });
    }

    private RolloverOperationRegistration resolveExistingInTransaction(
            long organizationId,
            String idempotencyKey,
            String requestHash
    ) {
        AcademicYearStructureRolloverOperation operation = repository
                .findByOrganizationIdAndIdempotencyKey(
                        organizationId,
                        idempotencyKey
                )
                .orElseThrow(() -> new RolloverIdempotencyKeyConflictException(
                        "Idempotency key could not be resolved"
                ));
        return resolveExisting(operation, requestHash);
    }

    private RolloverOperationRegistration resolveExisting(
            AcademicYearStructureRolloverOperation operation,
            String requestHash
    ) {
        if (!Objects.equals(operation.getRequestHash(), requestHash)) {
            throw new RolloverIdempotencyKeyConflictException(
                    "Idempotency key was already used for a different request"
            );
        }
        if (operation.getStatus() == RolloverOperationStatus.SUCCEEDED) {
            return new RolloverOperationRegistration(
                    operation.getId(),
                    true,
                    readResult(operation.getResultJson())
            );
        }
        if (operation.getStatus() == RolloverOperationStatus.RUNNING) {
            if (operation.getStartedAt() != null
                    && operation.getStartedAt()
                    .plus(runningTimeout)
                    .isBefore(OffsetDateTime.now())) {
                operation.markTerminalFailure(
                        RolloverOperationStatus.FAILED,
                        "OPERATION_INTERRUPTED",
                        "Previous rollover operation was interrupted"
                );
                repository.saveAndFlush(operation);
                throw new RolloverConflictException(
                        "Previous rollover operation was interrupted; use a new preview and idempotency key",
                        RolloverOperationStatus.FAILED,
                        "OPERATION_INTERRUPTED"
                );
            }
            throw new RolloverOperationAlreadyRunningException(
                    "Rollover operation is already running"
            );
        }
        throw new RolloverConflictException(
                "Previous rollover attempt with this idempotency key did not succeed",
                operation.getStatus(),
                operation.getFailureCode() == null
                        ? "PREVIOUS_ATTEMPT_FAILED"
                        : operation.getFailureCode()
        );
    }

    @Override
    public void markTerminalFailure(
            long organizationId,
            long operationId,
            RolloverOperationStatus status,
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
                                    != RolloverOperationStatus.SUCCEEDED) {
                                operation.markTerminalFailure(
                                        status,
                                        failureCode,
                                        failureMessage
                                );
                                repository.saveAndFlush(operation);
                            }
                        }));
    }

    private AcademicYearStructureRolloverResultResponse readResult(
            String resultJson
    ) {
        try {
            return objectMapper.readValue(
                    resultJson,
                    AcademicYearStructureRolloverResultResponse.class
            );
        } catch (Exception exception) {
            throw new RolloverConflictException(
                    "Stored rollover result could not be read",
                    RolloverOperationStatus.FAILED,
                    "RESULT_UNAVAILABLE"
            );
        }
    }
}
