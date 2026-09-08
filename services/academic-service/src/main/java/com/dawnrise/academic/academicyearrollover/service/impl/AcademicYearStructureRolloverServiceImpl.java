package com.dawnrise.academic.academicyearrollover.service.impl;

import com.dawnrise.academic.academicyearrollover.dto.*;
import com.dawnrise.academic.academicyearrollover.entity.AcademicYearStructureRolloverOperation;
import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;
import com.dawnrise.academic.academicyearrollover.exception.*;
import com.dawnrise.academic.academicyearrollover.repository.AcademicYearStructureRolloverOperationRepository;
import com.dawnrise.academic.academicyearrollover.service.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class AcademicYearStructureRolloverServiceImpl
        implements AcademicYearStructureRolloverService {

    private final RolloverPlanBuilder planBuilder;
    private final RolloverOperationLifecycleService lifecycleService;
    private final RolloverExecutor executor;
    private final AcademicYearStructureRolloverOperationRepository operationRepository;
    private final ObjectMapper objectMapper;

    public AcademicYearStructureRolloverServiceImpl(
            RolloverPlanBuilder planBuilder,
            RolloverOperationLifecycleService lifecycleService,
            RolloverExecutor executor,
            AcademicYearStructureRolloverOperationRepository operationRepository,
            ObjectMapper objectMapper
    ) {
        this.planBuilder = planBuilder;
        this.lifecycleService = lifecycleService;
        this.executor = executor;
        this.operationRepository = operationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AcademicYearStructureRolloverPreviewResponse preview(
            long organizationId,
            long targetAcademicYearId,
            AcademicYearStructureRolloverRequest request
    ) {
        return planBuilder.build(
                organizationId,
                targetAcademicYearId,
                request
        ).toPreviewResponse();
    }

    @Override
    public RolloverConfirmation confirm(
            long organizationId,
            long authenticatedUserId,
            long targetAcademicYearId,
            String idempotencyKey,
            ConfirmAcademicYearStructureRolloverRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);
        AcademicYearStructureRolloverRequest rolloverRequest =
                new AcademicYearStructureRolloverRequest(
                        request.sourceAcademicYearId(),
                        request.includeGradeLevels(),
                        request.includeSections(),
                        request.includeSubjects(),
                        request.includeGradeLevelSubjects(),
                        request.gradeLevelIds(),
                        request.subjectIds()
                );
        RolloverPlan initialPlan = planBuilder.build(
                organizationId,
                targetAcademicYearId,
                rolloverRequest
        );
        RolloverOperationRegistration registration =
                lifecycleService.registerOrResolve(
                        organizationId,
                        initialPlan.options().sourceAcademicYearId(),
                        initialPlan.options().targetAcademicYearId(),
                        idempotencyKey.trim(),
                        initialPlan.requestHash(),
                        request.previewFingerprint(),
                        authenticatedUserId
                );
        if (registration.replay()) {
            return new RolloverConfirmation(registration.replayResult(), true);
        }

        try {
            return new RolloverConfirmation(
                    executor.execute(
                            organizationId,
                            registration.operationId(),
                            request.previewFingerprint(),
                            rolloverRequest
                    ),
                    false
            );
        } catch (RolloverConflictException exception) {
            lifecycleService.markTerminalFailure(
                    organizationId,
                    registration.operationId(),
                    exception.getStatus(),
                    exception.getFailureCode(),
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            lifecycleService.markTerminalFailure(
                    organizationId,
                    registration.operationId(),
                    RolloverOperationStatus.FAILED,
                    "ROLLOVER_FAILED",
                    "Rollover operation failed"
            );
            throw exception;
        }
    }

    @Override
    public AcademicYearStructureRolloverOperationResponse getOperation(
            long organizationId,
            long operationId
    ) {
        AcademicYearStructureRolloverOperation operation =
                operationRepository.findByIdAndOrganizationId(
                                operationId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new RolloverOperationNotFoundException(
                                        "Rollover operation not found"
                                ));
        AcademicYearStructureRolloverResultResponse result = null;
        if (operation.getResultJson() != null) {
            try {
                result = objectMapper.readValue(
                        operation.getResultJson(),
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
        return new AcademicYearStructureRolloverOperationResponse(
                operation.getId(),
                operation.getStatus().name(),
                operation.getSourceAcademicYearId(),
                operation.getTargetAcademicYearId(),
                operation.getRequestHash(),
                operation.getPreviewFingerprint(),
                operation.getFailureCode(),
                operation.getFailureMessage(),
                result == null ? null : result.counts(),
                result == null ? null : result.mappings(),
                operation.getCreatedAt(),
                operation.getStartedAt(),
                operation.getCompletedAt(),
                operation.getUpdatedAt()
        );
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRolloverRequestException(
                    "Idempotency-Key header is required"
            );
        }
        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.length() > 128) {
            throw new InvalidRolloverRequestException(
                    "Idempotency-Key cannot exceed 128 characters"
            );
        }
        for (int i = 0; i < normalizedKey.length(); i++) {
            if (Character.isISOControl(normalizedKey.charAt(i))) {
                throw new InvalidRolloverRequestException(
                        "Idempotency-Key cannot contain control characters"
                );
            }
        }
    }
}
