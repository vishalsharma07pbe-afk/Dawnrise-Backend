package com.dawnrise.academic.studentprogression.dto;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;

import java.util.List;

public record StudentProgressionConfirmationResponse(

        Long operationId,
        StudentProgressionOperationStatus status,

        Long sourceAcademicYearId,
        Long targetAcademicYearId,
        String batchLabel,

        String requestHash,
        String previewFingerprint,

        StudentProgressionCountsResponse counts,
        List<StudentProgressionItemResultResponse> items
) {
    public StudentProgressionConfirmationResponse {
        items = items == null
                ? List.of()
                : List.copyOf(items);
    }
}