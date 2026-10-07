package com.dawnrise.academic.examination.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateScheduledAssessmentRequest(
        @NotNull @Positive Long gradeLevelId,
        @NotNull @Positive Long gradeLevelSubjectId,
        @NotNull LocalDate assessmentDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @DecimalMin("0.01") @DecimalMax("99999.99")
        @Digits(integer = 5, fraction = 2) BigDecimal maximumMarks
) {}
