package com.dawnrise.academic.subject.dto;

import java.time.OffsetDateTime;

public record SubjectResponse(
        Long id,
        Long academicYearId,
        String code,
        String name,
        String description,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}