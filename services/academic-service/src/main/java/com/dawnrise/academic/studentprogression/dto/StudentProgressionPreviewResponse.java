package com.dawnrise.academic.studentprogression.dto;

import java.util.List;

public record StudentProgressionPreviewResponse(

        Long sourceAcademicYearId,
        Long targetAcademicYearId,
        String batchLabel,

        StudentProgressionCountsResponse counts,

        boolean canConfirm,
        String previewFingerprint,

        List<StudentProgressionItemPreviewResponse> items,
        List<StudentProgressionConflictResponse> conflicts
) {
    public StudentProgressionPreviewResponse {
        items = items == null
                ? List.of()
                : List.copyOf(items);

        conflicts = conflicts == null
                ? List.of()
                : List.copyOf(conflicts);
    }
}