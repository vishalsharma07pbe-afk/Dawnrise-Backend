package com.dawnrise.academic.section.dto;

import java.time.OffsetDateTime;

public record SectionResponse(
        Long id,
        Long academicYearId,
        Long gradeLevelId,
        String code,
        String name,
        Integer displayOrder,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}