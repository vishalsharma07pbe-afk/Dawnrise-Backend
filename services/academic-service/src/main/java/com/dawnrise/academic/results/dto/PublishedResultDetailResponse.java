package com.dawnrise.academic.results.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PublishedResultDetailResponse(
        long publicationId,
        long academicYearId,
        String academicYearName,
        long examinationId,
        String examinationName,
        long gradeLevelId,
        String gradeLevelName,
        long sectionId,
        String sectionName,
        OffsetDateTime publishedAt,
        long studentUserId,
        long enrollmentId,
        String rollNumberSnapshot,
        List<PublishedResultSubjectResponse> subjects,
        BigDecimal totalMaximumMarks,
        BigDecimal totalObtainedMarks,
        int absentSubjectCount
) {
}
