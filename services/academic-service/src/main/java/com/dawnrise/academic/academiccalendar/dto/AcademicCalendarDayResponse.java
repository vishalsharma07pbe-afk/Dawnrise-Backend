package com.dawnrise.academic.academiccalendar.dto;

import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.enums.CalendarDayType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AcademicCalendarDayResponse(
        Long id,
        Long academicYearId,
        LocalDate calendarDate,
        CalendarDayType dayType,
        String name,
        AttendanceRequirement attendanceRequirement,
        boolean countsTowardPercentage,
        BigDecimal dayWeight,
        String note,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
