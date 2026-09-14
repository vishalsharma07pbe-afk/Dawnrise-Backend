package com.dawnrise.academic.studentattendance.reporting.dto;

import java.time.YearMonth;
import java.util.Objects;

public record MonthlyStudentAttendanceReportResponse(
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        Long studentUserId,
        YearMonth month,
        long submittedAttendanceDays,
        AttendanceSummary summary
) {

    public MonthlyStudentAttendanceReportResponse {
        requirePositive(organizationId, "Organization ID");
        requirePositive(academicYearId, "Academic year ID");
        requirePositive(gradeLevelId, "Grade level ID");
        requirePositive(sectionId, "Section ID");
        requirePositive(studentUserId, "Student user ID");
        Objects.requireNonNull(
                month,
                "Report month is required"
        );
        Objects.requireNonNull(
                summary,
                "Attendance summary is required"
        );
        if (submittedAttendanceDays < 0) {
            throw new IllegalArgumentException(
                    "Submitted attendance days cannot be negative"
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
