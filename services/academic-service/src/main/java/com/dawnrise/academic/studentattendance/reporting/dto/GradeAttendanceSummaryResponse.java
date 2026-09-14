package com.dawnrise.academic.studentattendance.reporting.dto;

import java.util.List;
import java.util.Objects;

public record GradeAttendanceSummaryResponse(
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        AttendanceReportDateRange dateRange,
        AttendanceSummary summary,
        List<GradeAttendanceSectionRowResponse> sections
) {

    public GradeAttendanceSummaryResponse {
        requirePositive(organizationId, "Organization ID");
        requirePositive(academicYearId, "Academic year ID");
        requirePositive(gradeLevelId, "Grade level ID");
        Objects.requireNonNull(
                dateRange,
                "Attendance date range is required"
        );
        Objects.requireNonNull(
                summary,
                "Attendance summary is required"
        );
        sections = sections == null
                ? List.of()
                : List.copyOf(sections);
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
