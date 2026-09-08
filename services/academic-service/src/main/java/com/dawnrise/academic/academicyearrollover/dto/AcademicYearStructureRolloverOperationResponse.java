package com.dawnrise.academic.academicyearrollover.dto;

import java.time.OffsetDateTime;

public record AcademicYearStructureRolloverOperationResponse(
        Long operationId,
        String status,
        Long sourceAcademicYearId,
        Long targetAcademicYearId,
        String requestHash,
        String previewFingerprint,
        String failureCode,
        String failureMessage,
        RolloverCountsResponse counts,
        RolloverMappingsResponse mappings,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
