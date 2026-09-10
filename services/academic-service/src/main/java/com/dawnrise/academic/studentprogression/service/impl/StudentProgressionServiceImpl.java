package com.dawnrise.academic.studentprogression.service.impl;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionPreviewRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionPreviewResponse;
import com.dawnrise.academic.studentprogression.mapper.StudentProgressionPreviewMapper;
import com.dawnrise.academic.studentprogression.service.StudentProgressionConfirmationCoordinator;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlan;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanBuilder;
import com.dawnrise.academic.studentprogression.service.StudentProgressionService;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionOperationResponse;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionOperation;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionOperationNotFoundException;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionOperationRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProgressionServiceImpl
        implements StudentProgressionService {

    private final StudentProgressionOperationRepository operationRepository;
    private final ObjectMapper objectMapper;
    private final StudentProgressionPlanBuilder planBuilder;
    private final StudentProgressionPreviewMapper previewMapper;
    private final StudentProgressionConfirmationCoordinator
            confirmationCoordinator;

    public StudentProgressionServiceImpl(
            StudentProgressionPlanBuilder planBuilder,
            StudentProgressionPreviewMapper previewMapper,
            StudentProgressionConfirmationCoordinator confirmationCoordinator,
            StudentProgressionOperationRepository operationRepository,
            ObjectMapper objectMapper
    ) {
        this.planBuilder = planBuilder;
        this.previewMapper = previewMapper;
        this.confirmationCoordinator = confirmationCoordinator;
        this.operationRepository = operationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentProgressionPreviewResponse preview(
            long organizationId,
            long targetAcademicYearId,
            StudentProgressionPreviewRequest request
    ) {
        StudentProgressionPlan plan =
                planBuilder.buildForPreview(
                        organizationId,
                        targetAcademicYearId,
                        request
                );

        return previewMapper.toResponse(plan);
    }

    @Override
    public StudentProgressionConfirmationResponse confirm(
            long organizationId,
            long targetAcademicYearId,
            long authenticatedUserId,
            String idempotencyKey,
            StudentProgressionConfirmationRequest request
    ) {
        return confirmationCoordinator.confirm(
                organizationId,
                targetAcademicYearId,
                authenticatedUserId,
                idempotencyKey,
                request
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StudentProgressionOperationResponse getOperation(
            long organizationId,
            long operationId
    ) {
        StudentProgressionOperation operation =
                operationRepository
                        .findByIdAndOrganizationId(
                                operationId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new StudentProgressionOperationNotFoundException(
                                        "Student progression operation not found"
                                )
                        );

        StudentProgressionConfirmationResponse result =
                readResult(operation);

        return new StudentProgressionOperationResponse(
                operation.getId(),
                operation.getSourceAcademicYearId(),
                operation.getTargetAcademicYearId(),
                operation.getBatchLabel(),
                operation.getStatus(),
                operation.getRequestedByUserId(),
                operation.getTotalItems(),
                operation.getRequestHash(),
                operation.getPreviewFingerprint(),
                operation.getFailureCode(),
                operation.getFailureMessage(),
                result,
                operation.getCreatedAt(),
                operation.getStartedAt(),
                operation.getCompletedAt(),
                operation.getUpdatedAt()
        );
    }

    private StudentProgressionConfirmationResponse readResult(
            StudentProgressionOperation operation
    ) {
        if (operation.getStatus()
                != StudentProgressionOperationStatus.SUCCEEDED) {
            return null;
        }

        try {
            return objectMapper.readValue(
                    operation.getResultJson(),
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
}
