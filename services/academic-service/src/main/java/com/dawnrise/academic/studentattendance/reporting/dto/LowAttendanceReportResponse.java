package com.dawnrise.academic.studentattendance.reporting.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record LowAttendanceReportResponse(
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        AttendanceReportDateRange dateRange,
        BigDecimal thresholdPercentage,
        AttendancePageResponse<LowAttendanceStudentResponse> students
) {

    public LowAttendanceReportResponse {
        requirePositive(organizationId, "Organization ID");
        requirePositive(academicYearId, "Academic year ID");
        requireOptionalPositive(gradeLevelId, "Grade level ID");
        requireOptionalPositive(sectionId, "Section ID");
        Objects.requireNonNull(
                dateRange,
                "Attendance date range is required"
        );
        Objects.requireNonNull(
                students,
                "Low-attendance student page is required"
        );
        if (thresholdPercentage == null
                || thresholdPercentage.signum() < 0
                || thresholdPercentage.compareTo(
                BigDecimal.valueOf(100)
        ) > 0) {
            throw new IllegalArgumentException(
                    "Attendance threshold must be between 0 and 100"
            );
        }
        thresholdPercentage = thresholdPercentage.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private static void requirePositive(
            Long value,
            String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }

    private static void requireOptionalPositive(
            Long value,
            String fieldName
    ) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}
