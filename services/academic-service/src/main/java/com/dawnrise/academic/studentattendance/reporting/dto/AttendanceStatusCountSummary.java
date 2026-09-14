package com.dawnrise.academic.studentattendance.reporting.dto;

public record AttendanceStatusCountSummary(
        long presentCount,
        long absentCount,
        long lateCount,
        long halfDayCount,
        long excusedCount,
        long totalCount
) {

    public AttendanceStatusCountSummary {
        validateNonNegative(presentCount, "Present count");
        validateNonNegative(absentCount, "Absent count");
        validateNonNegative(lateCount, "Late count");
        validateNonNegative(halfDayCount, "Half-day count");
        validateNonNegative(excusedCount, "Excused count");

        totalCount = Math.addExact(
                Math.addExact(
                        Math.addExact(presentCount, absentCount),
                        Math.addExact(lateCount, halfDayCount)
                ),
                excusedCount
        );
    }

    public static AttendanceStatusCountSummary of(
            long presentCount,
            long absentCount,
            long lateCount,
            long halfDayCount,
            long excusedCount
    ) {
        return new AttendanceStatusCountSummary(
                presentCount,
                absentCount,
                lateCount,
                halfDayCount,
                excusedCount,
                0
        );
    }

    public static AttendanceStatusCountSummary empty() {
        return of(0, 0, 0, 0, 0);
    }

    private static void validateNonNegative(
            long value,
            String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be negative"
            );
        }
    }
}