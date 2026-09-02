package com.dawnrise.academic.gradelevel.dto;

import java.time.OffsetDateTime;

public record GradeLevelResponse(
        Long id,
        Long academicYearId,
        String code,
        String name,
        Integer displayOrder,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}