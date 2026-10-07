package com.dawnrise.academic.examination.dto;

import com.dawnrise.academic.examination.enums.ExaminationStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ExaminationResponse(
        Long id,
        Long academicYearId,
        String name,
        ExaminationStatus status,
        OffsetDateTime publishedAt,
        Long version,
        List<ScheduledAssessmentResponse> assessments,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
