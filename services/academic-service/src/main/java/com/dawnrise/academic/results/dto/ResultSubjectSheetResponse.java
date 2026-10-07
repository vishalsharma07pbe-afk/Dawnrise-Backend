package com.dawnrise.academic.results.dto;

import com.dawnrise.academic.results.enums.ResultSubjectSheetStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ResultSubjectSheetResponse(
        Long id,
        Long academicYearId,
        Long examinationId,
        Long scheduledAssessmentId,
        Long gradeLevelId,
        Long sectionId,
        Long gradeLevelSubjectId,
        BigDecimal maximumMarks,
        LocalDate assessmentDate,
        ResultSubjectSheetStatus status,
        Long submittedByUserId,
        OffsetDateTime submittedAt,
        Long reviewedByUserId,
        OffsetDateTime reviewedAt,
        String reviewNote,
        Long version,
        List<ResultSubjectMarkResponse> marks,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
