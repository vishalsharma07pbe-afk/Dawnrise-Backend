package com.dawnrise.academic.studentattendance.reporting.dto;

import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;

import java.time.LocalDate;
import java.util.Objects;

public record MissingAttendanceSessionResponse(
        Long academicCalendarDayId,
        LocalDate attendanceDate,
        Long gradeLevelId,
        Long sectionId,
        String sectionCode,
        String sectionName,
        Long attendanceSessionId,
        StudentAttendanceSessionStatus lifecycleStatus,
        long eligibleStudentCount,
        long recordedStudentCount,
        boolean sessionMissing,
        boolean rosterIncomplete
) {

    public MissingAttendanceSessionResponse {
        requirePositive(academicCalendarDayId, "Academic calendar day ID");
        Objects.requireNonNull(
                attendanceDate,
                "Attendance date is required"
        );
        requirePositive(gradeLevelId, "Grade level ID");
        requirePositive(sectionId, "Section ID");
        requireOptionalPositive(attendanceSessionId, "Attendance session ID");
        if (attendanceSessionId == null && lifecycleStatus != null) {
            throw new IllegalArgumentException(
                    "Missing attendance session cannot have a lifecycle status"
            );
        }
        if (attendanceSessionId != null && lifecycleStatus == null) {
            throw new IllegalArgumentException(
                    "Attendance session lifecycle status is required"
            );
        }
        if (sectionCode == null || sectionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Section code is required"
            );
        }

        if (sectionName == null || sectionName.isBlank()) {
            throw new IllegalArgumentException(
                    "Section name is required"
            );
        }

        if (eligibleStudentCount < 0
                || recordedStudentCount < 0) {
            throw new IllegalArgumentException(
                    "Attendance roster counts cannot be negative"
            );
        }

        sectionCode = sectionCode.trim();
        sectionName = sectionName.trim();
        sessionMissing = attendanceSessionId == null;
        rosterIncomplete = attendanceSessionId != null
                && recordedStudentCount != eligibleStudentCount;
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
