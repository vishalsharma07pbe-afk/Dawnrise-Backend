package com.dawnrise.academic.studentattendance.reporting.dto;

import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record DailySectionAttendanceReportResponse(
        Long attendanceSessionId,
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        Long academicCalendarDayId,
        LocalDate attendanceDate,
        StudentAttendanceSessionStatus lifecycleStatus,
        StudentAttendanceSubmissionType submissionType,
        OffsetDateTime submittedAt,
        AttendanceSummary summary,
        List<DailyStudentAttendanceRowResponse> students
) {

    public DailySectionAttendanceReportResponse {
        requirePositive(attendanceSessionId, "Attendance session ID");
        requirePositive(organizationId, "Organization ID");
        requirePositive(academicYearId, "Academic year ID");
        requirePositive(gradeLevelId, "Grade level ID");
        requirePositive(sectionId, "Section ID");
        requirePositive(academicCalendarDayId, "Academic calendar day ID");
        Objects.requireNonNull(
                attendanceDate,
                "Attendance date is required"
        );
        Objects.requireNonNull(
                lifecycleStatus,
                "Attendance session lifecycle status is required"
        );
        Objects.requireNonNull(
                summary,
                "Attendance summary is required"
        );
        students = students == null
                ? List.of()
                : List.copyOf(students);
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
