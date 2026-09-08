package com.dawnrise.academic.academicyearrollover.dto;

public record ProposedSubjectResponse(
        Long sourceId,
        String code,
        String name,
        String description
) {
}
