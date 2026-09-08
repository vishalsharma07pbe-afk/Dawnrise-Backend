package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyearrollover.dto.*;

public interface AcademicYearStructureRolloverService {
    AcademicYearStructureRolloverPreviewResponse preview(
            long organizationId,
            long targetAcademicYearId,
            AcademicYearStructureRolloverRequest request
    );

    RolloverConfirmation confirm(
            long organizationId,
            long authenticatedUserId,
            long targetAcademicYearId,
            String idempotencyKey,
            ConfirmAcademicYearStructureRolloverRequest request
    );

    AcademicYearStructureRolloverOperationResponse getOperation(
            long organizationId,
            long operationId
    );
}
