package com.dawnrise.academic.results.dto;

import java.time.OffsetDateTime;

public record PublishedResultSummaryResponse(
        long publicationId,
        long academicYearId,
        String academicYearName,
        long examinationId,
        String examinationName,
        long gradeLevelId,
        String gradeLevelName,
        long sectionId,
        String sectionName,
        OffsetDateTime publishedAt
) {
}
