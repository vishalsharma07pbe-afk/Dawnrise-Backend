package com.dawnrise.academic.academicyearrollover.dto;

public record ProposedGradeLevelResponse(
        Long sourceId,
        String code,
        String name,
        Integer displayOrder
) {
}
