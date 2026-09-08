package com.dawnrise.academic.academicyearrollover.dto;

import java.util.List;

public record AcademicYearStructureRolloverPreviewResponse(
        Long sourceAcademicYearId,
        Long targetAcademicYearId,
        boolean canConfirm,
        String requestHash,
        String previewFingerprint,
        RolloverCountsResponse counts,
        RolloverProposedRecordsResponse proposedRecords,
        List<RolloverConflictResponse> conflicts
) {
}
