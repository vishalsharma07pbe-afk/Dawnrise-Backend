package com.dawnrise.academic.gradelevelsubject.dto;

import java.time.OffsetDateTime;

public record GradeLevelSubjectResponse(
        Long id,
        Long academicYearId,
        Long gradeLevelId,
        Long subjectId,
        Boolean mandatory,
        Integer displayOrder,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}