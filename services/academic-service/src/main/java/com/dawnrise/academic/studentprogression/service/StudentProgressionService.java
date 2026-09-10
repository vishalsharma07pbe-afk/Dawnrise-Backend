package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.dto.*;

public interface StudentProgressionService {

    StudentProgressionPreviewResponse preview(
            long organizationId,
            long targetAcademicYearId,
            StudentProgressionPreviewRequest request
    );

    StudentProgressionConfirmationResponse confirm(
            long organizationId,
            long targetAcademicYearId,
            long authenticatedUserId,
            String idempotencyKey,
            StudentProgressionConfirmationRequest request
    );

    StudentProgressionOperationResponse getOperation(
            long organizationId,
            long operationId
    );
}