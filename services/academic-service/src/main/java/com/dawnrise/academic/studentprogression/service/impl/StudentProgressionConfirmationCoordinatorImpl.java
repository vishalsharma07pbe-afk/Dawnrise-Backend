package com.dawnrise.academic.studentprogression.service.impl;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import com.dawnrise.academic.studentprogression.service.StudentProgressionConfirmationCoordinator;
import com.dawnrise.academic.studentprogression.service.StudentProgressionExecutor;
import com.dawnrise.academic.studentprogression.service.StudentProgressionFingerprintService;
import com.dawnrise.academic.studentprogression.service.StudentProgressionOperationLifecycleService;
import com.dawnrise.academic.studentprogression.service.StudentProgressionOperationRegistration;
import org.springframework.stereotype.Service;

@Service
public class StudentProgressionConfirmationCoordinatorImpl
        implements StudentProgressionConfirmationCoordinator {

    private final StudentProgressionFingerprintService fingerprintService;
    private final StudentProgressionOperationLifecycleService lifecycleService;
    private final StudentProgressionExecutor executor;

    public StudentProgressionConfirmationCoordinatorImpl(
            StudentProgressionFingerprintService fingerprintService,
            StudentProgressionOperationLifecycleService lifecycleService,
            StudentProgressionExecutor executor
    ) {
        this.fingerprintService = fingerprintService;
        this.lifecycleService = lifecycleService;
        this.executor = executor;
    }

    @Override
    public StudentProgressionConfirmationResponse confirm(
            long organizationId,
            long targetAcademicYearId,
            long authenticatedUserId,
            String idempotencyKey,
            StudentProgressionConfirmationRequest request
    ) {
        String requestHash = fingerprintService.requestHash(
                organizationId,
                request.sourceAcademicYearId(),
                targetAcademicYearId,
                request.batchLabel(),
                request.decisions()
        );

        StudentProgressionOperationRegistration registration =
                lifecycleService.registerOrResolve(
                        organizationId,
                        request.sourceAcademicYearId(),
                        targetAcademicYearId,
                        request.batchLabel(),
                        idempotencyKey,
                        requestHash,
                        request.previewFingerprint(),
                        authenticatedUserId,
                        request.decisions().size()
                );

        if (registration.replay()) {
            return registration.replayResult();
        }

        try {
            return executor.execute(
                    organizationId,
                    registration.operationId(),
                    targetAcademicYearId,
                    requestHash,
                    request
            );
        } catch (StudentProgressionConflictException exception) {
            lifecycleService.markTerminalFailure(
                    organizationId,
                    registration.operationId(),
                    exception.getOperationStatus(),
                    exception.getFailureCode(),
                    safeMessage(exception)
            );

            throw exception;
        } catch (RuntimeException exception) {
            lifecycleService.markTerminalFailure(
                    organizationId,
                    registration.operationId(),
                    StudentProgressionOperationStatus.FAILED,
                    "EXECUTION_FAILED",
                    safeMessage(exception)
            );

            throw exception;
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return "Student progression execution failed";
        }

        String normalized = message.trim();

        return normalized.length() <= 500
                ? normalized
                : normalized.substring(0, 500);
    }
}