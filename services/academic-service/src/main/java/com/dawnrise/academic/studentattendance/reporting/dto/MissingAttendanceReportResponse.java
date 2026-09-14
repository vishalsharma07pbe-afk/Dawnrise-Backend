package com.dawnrise.academic.studentattendance.reporting.dto;

import java.util.List;
import java.util.Objects;

public record MissingAttendanceReportResponse(
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        AttendanceReportDateRange dateRange,
        int pageNumber,
        int pageSize,
        boolean hasNext,
        List<MissingAttendanceSessionResponse> items
) {

    public MissingAttendanceReportResponse {
        requirePositive(organizationId, "Organization ID");
        requirePositive(academicYearId, "Academic year ID");
        requireOptionalPositive(gradeLevelId, "Grade level ID");
        requireOptionalPositive(sectionId, "Section ID");
        Objects.requireNonNull(
                dateRange,
                "Attendance date range is required"
        );
        if (pageNumber < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (pageSize <= 0) {
            throw new IllegalArgumentException(
                    "Page size must be positive"
            );
        }

        items = items == null
                ? List.of()
                : List.copyOf(items);
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
