package com.dawnrise.academic.examination.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record ScheduledAssessmentResponse(
        Long id,
        Long gradeLevelId,
        Long gradeLevelSubjectId,
        LocalDate assessmentDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal maximumMarks,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
