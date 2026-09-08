package com.dawnrise.academic.academicyearrollover.dto;

public record ProposedSectionResponse(
        Long sourceId,
        Long sourceGradeLevelId,
        String code,
        String name,
        Integer displayOrder
) {
}
