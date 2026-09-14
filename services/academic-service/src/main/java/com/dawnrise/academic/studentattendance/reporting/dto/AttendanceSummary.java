package com.dawnrise.academic.studentattendance.reporting.dto;

import java.math.BigDecimal;
import java.util.Objects;

public record AttendanceSummary(
        AttendanceStatusCountSummary statusCounts,
        AttendanceCreditSummary credits
) {

    public AttendanceSummary {
        Objects.requireNonNull(
                statusCounts,
                "Attendance status counts are required"
        );

        Objects.requireNonNull(
                credits,
                "Attendance credit summary is required"
        );
    }

    public static AttendanceSummary of(
            long presentCount,
            long absentCount,
            long lateCount,
            long halfDayCount,
            long excusedCount,
            BigDecimal earnedCredit,
            BigDecimal possibleCredit
    ) {
        return new AttendanceSummary(
                AttendanceStatusCountSummary.of(
                        presentCount,
                        absentCount,
                        lateCount,
                        halfDayCount,
                        excusedCount
                ),
                AttendanceCreditSummary.from(
                        earnedCredit,
                        possibleCredit
                )
        );
    }

    public static AttendanceSummary empty() {
        return of(
                0,
                0,
                0,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}