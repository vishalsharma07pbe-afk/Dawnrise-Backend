package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyearrollover.dto.AcademicYearStructureRolloverResultResponse;

public record RolloverConfirmation(
        AcademicYearStructureRolloverResultResponse response,
        boolean replay
) {
}
