package com.dawnrise.academic.academiccalendar.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Set;

public record InitializeAcademicCalendarRequest(
        @NotEmpty Set<DayOfWeek> workingWeekDays,
        @DecimalMin("0.01") @DecimalMax("1.00") BigDecimal defaultWorkingDayWeight
) {
}
