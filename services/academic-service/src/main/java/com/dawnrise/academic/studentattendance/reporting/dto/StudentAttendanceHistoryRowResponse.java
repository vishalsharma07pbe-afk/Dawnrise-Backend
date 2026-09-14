package com.dawnrise.academic.studentattendance.reporting.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

public record StudentAttendanceHistoryRowResponse(
        Long attendanceSessionId,
        Long attendanceRecordId,
        Long studentEnrollmentId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        LocalDate attendanceDate,
        AttendanceStatus recordedStatus,
        AttendanceStatus effectiveStatus,
        BigDecimal earnedCredit,
        BigDecimal possibleCredit,
        boolean latePenaltyApplied,
        String remarks,
        StudentAttendanceSubmissionType submissionType,
        OffsetDateTime submittedAt,
        OffsetDateTime lastUpdatedAt
) {
    public StudentAttendanceHistoryRowResponse {
        requirePositive(attendanceSessionId, "Attendance session ID");
        requirePositive(attendanceRecordId, "Attendance record ID");
        requirePositive(studentEnrollmentId, "Student enrollment ID");
        requirePositive(academicYearId, "Academic year ID");
        requirePositive(gradeLevelId, "Grade level ID");
        requirePositive(sectionId, "Section ID");
        Objects.requireNonNull(
                attendanceDate,
                "Attendance date is required"
        );
        Objects.requireNonNull(
                recordedStatus,
                "Recorded attendance status is required"
        );
        Objects.requireNonNull(
                effectiveStatus,
                "Effective attendance status is required"
        );
        if (earnedCredit == null || possibleCredit == null) {
            throw new IllegalArgumentException(
                    "Attendance credits are required"
            );
        }
        if (earnedCredit.signum() < 0
                || possibleCredit.signum() < 0
                || earnedCredit.compareTo(possibleCredit) > 0) {
            throw new IllegalArgumentException(
                    "Attendance credits are invalid"
            );
        }
        if (submissionType == null) {
            throw new IllegalArgumentException(
                    "Attendance submission type is required"
            );
        }
        if (submittedAt == null) {
            throw new IllegalArgumentException(
                    "Submitted timestamp is required"
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
