package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyearrollover.dto.AcademicYearStructureRolloverResultResponse;

public record RolloverOperationRegistration(
        Long operationId,
        boolean replay,
        AcademicYearStructureRolloverResultResponse replayResult
) {
}
