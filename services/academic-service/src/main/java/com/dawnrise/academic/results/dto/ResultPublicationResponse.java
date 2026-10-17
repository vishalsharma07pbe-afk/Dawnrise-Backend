package com.dawnrise.academic.results.dto;

import com.dawnrise.academic.results.enums.ResultPublicationStatus;

import java.time.OffsetDateTime;

public record ResultPublicationResponse(
        long id,
        long academicYearId,
        long examinationId,
        long gradeLevelId,
        long sectionId,
        ResultPublicationStatus status,
        long publishedByUserId,
        OffsetDateTime publishedAt,
        long version
) {
}
