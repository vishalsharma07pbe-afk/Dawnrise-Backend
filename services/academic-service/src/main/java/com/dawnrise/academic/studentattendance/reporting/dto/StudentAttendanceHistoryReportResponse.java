package com.dawnrise.academic.studentattendance.reporting.dto;

import java.util.Objects;

public record StudentAttendanceHistoryReportResponse(
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        Long studentUserId,
        AttendanceReportDateRange dateRange,
        AttendanceSummary summary,
        AttendancePageResponse<StudentAttendanceHistoryRowResponse> records
) {
    public StudentAttendanceHistoryReportResponse {
        requirePositive(organizationId, "Organization ID");
        requirePositive(academicYearId, "Academic year ID");
        requirePositive(gradeLevelId, "Grade level ID");
        requirePositive(sectionId, "Section ID");
        requirePositive(studentUserId, "Student user ID");
        Objects.requireNonNull(
                dateRange,
                "Attendance date range is required"
        );
        Objects.requireNonNull(
                summary,
                "Attendance summary is required"
        );
        Objects.requireNonNull(
                records,
                "Student attendance history page is required"
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
}
