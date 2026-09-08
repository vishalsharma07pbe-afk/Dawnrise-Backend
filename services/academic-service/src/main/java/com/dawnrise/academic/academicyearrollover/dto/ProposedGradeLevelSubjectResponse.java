package com.dawnrise.academic.academicyearrollover.dto;

public record ProposedGradeLevelSubjectResponse(
        Long sourceId,
        Long sourceGradeLevelId,
        Long sourceSubjectId,
        Boolean mandatory,
        Integer displayOrder
) {
}
