package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyearrollover.dto.AcademicYearStructureRolloverResultResponse;
import com.dawnrise.academic.academicyearrollover.dto.AcademicYearStructureRolloverRequest;

public interface RolloverExecutor {
    AcademicYearStructureRolloverResultResponse execute(
            long organizationId,
            long operationId,
            String submittedPreviewFingerprint,
            AcademicYearStructureRolloverRequest request
    );
}
