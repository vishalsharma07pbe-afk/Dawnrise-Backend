package com.dawnrise.academic.studentprogression.dto;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;

import java.time.OffsetDateTime;

public record StudentProgressionOperationResponse(

        Long operationId,
        Long sourceAcademicYearId,
        Long targetAcademicYearId,
        String batchLabel,

        StudentProgressionOperationStatus status,
        Long requestedByUserId,
        Integer totalItems,

        String requestHash,
        String previewFingerprint,

        String failureCode,
        String failureMessage,

        StudentProgressionConfirmationResponse result,

        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}