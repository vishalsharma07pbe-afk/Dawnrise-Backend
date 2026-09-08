package com.dawnrise.academic.academicyearrollover.dto;

public record AcademicYearStructureRolloverResultResponse(
        Long operationId,
        String status,
        Long sourceAcademicYearId,
        Long targetAcademicYearId,
        String requestHash,
        String previewFingerprint,
        RolloverCountsResponse counts,
        RolloverMappingsResponse mappings
) {
}
