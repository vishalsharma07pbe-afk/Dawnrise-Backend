package com.dawnrise.academic.studentattendance.reporting.dto;

import java.util.Objects;

public record SectionAttendanceSummaryResponse(
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        AttendanceReportDateRange dateRange,
        long submittedSessionCount,
        long distinctStudentCount,
        AttendanceSummary summary
) {

    public SectionAttendanceSummaryResponse {
        requirePositive(organizationId, "Organization ID");
        requirePositive(academicYearId, "Academic year ID");
        requirePositive(gradeLevelId, "Grade level ID");
        requirePositive(sectionId, "Section ID");
        Objects.requireNonNull(
                dateRange,
                "Attendance date range is required"
        );
        Objects.requireNonNull(
                summary,
                "Attendance summary is required"
        );
        if (submittedSessionCount < 0) {
            throw new IllegalArgumentException(
                    "Submitted session count cannot be negative"
            );
        }

        if (distinctStudentCount < 0) {
            throw new IllegalArgumentException(
                    "Distinct student count cannot be negative"
            );
        }
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
}
