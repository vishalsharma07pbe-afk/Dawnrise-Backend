package com.dawnrise.academic.academiccalendar.dto;

import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.enums.CalendarDayType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateAcademicCalendarDayRequest(
        @NotNull CalendarDayType dayType,
        @Size(max = 150) String name,
        @NotNull AttendanceRequirement attendanceRequirement,
        boolean countsTowardPercentage,
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal dayWeight,
        @Size(max = 500) String note,
        @NotNull Long expectedVersion
) {
}
