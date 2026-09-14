package com.dawnrise.academic.studentattendance.reporting.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record AttendanceReportDateRange(
        LocalDate fromDate,
        LocalDate toDate
) {

    public AttendanceReportDateRange {
        Objects.requireNonNull(
                fromDate,
                "Report start date is required"
        );

        Objects.requireNonNull(
                toDate,
                "Report end date is required"
        );

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "Report start date must not be after end date"
            );
        }
    }

    public long inclusiveDayCount() {
        return ChronoUnit.DAYS.between(
                fromDate,
                toDate
        ) + 1;
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(
                date,
                "Attendance date is required"
        );

        return !date.isBefore(fromDate)
                && !date.isAfter(toDate);
    }
}