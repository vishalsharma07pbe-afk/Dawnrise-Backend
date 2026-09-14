package com.dawnrise.academic.studentattendance.notificationoutbox.payload;

import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventSource;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventType;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record StudentAttendanceNotificationPayload(
        int schemaVersion,
        StudentAttendanceNotificationEventType eventType,
        StudentAttendanceNotificationEventSource eventSource,
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        Long attendanceSessionId,
        Long attendanceRecordId,
        Long correctionRequestId,
        Long studentEnrollmentId,
        Long studentUserId,
        LocalDate attendanceDate,
        AttendanceStatus previousRecordedStatus,
        AttendanceStatus previousEffectiveStatus,
        Boolean previousLatePenaltyApplied,
        AttendanceStatus recordedStatus,
        AttendanceStatus effectiveStatus,
        BigDecimal earnedCredit,
        BigDecimal possibleCredit,
        boolean latePenaltyApplied,
        StudentAttendanceSubmissionType submissionType,
        OffsetDateTime occurredAt
) {

    public StudentAttendanceNotificationPayload {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    "Unsupported notification payload schema version"
            );
        }

        requireNonNull(eventType, "Event type is required");
        requireNonNull(eventSource, "Event source is required");
        requirePositive(organizationId, "Organization ID");
        requirePositive(academicYearId, "Academic year ID");
        requirePositive(gradeLevelId, "Grade level ID");
        requirePositive(sectionId, "Section ID");
        requirePositive(attendanceSessionId, "Attendance session ID");
        requirePositive(attendanceRecordId, "Attendance record ID");
        requirePositive(studentEnrollmentId, "Student enrollment ID");
        requirePositive(studentUserId, "Student user ID");

        requireNonNull(
                attendanceDate,
                "Attendance date is required"
        );
        requireNonNull(
                recordedStatus,
                "Recorded status is required"
        );
        requireNonNull(
                effectiveStatus,
                "Effective status is required"
        );
        requireNonNull(
                submissionType,
                "Submission type is required"
        );
        requireNonNull(
                occurredAt,
                "Occurred timestamp is required"
        );

        validateCredit(earnedCredit, "Earned credit");
        validateCredit(possibleCredit, "Possible credit");

        if (earnedCredit.compareTo(possibleCredit) > 0) {
            throw new IllegalArgumentException(
                    "Earned credit cannot exceed possible credit"
            );
        }

        if (eventSource
                == StudentAttendanceNotificationEventSource
                .CORRECTION_APPROVAL) {
            requirePositive(
                    correctionRequestId,
                    "Correction request ID"
            );
            requireNonNull(
                    previousRecordedStatus,
                    "Previous recorded status is required"
            );
            requireNonNull(
                    previousEffectiveStatus,
                    "Previous effective status is required"
            );
            requireNonNull(
                    previousLatePenaltyApplied,
                    "Previous late-penalty state is required"
            );
        } else if (correctionRequestId != null
                || previousRecordedStatus != null
                || previousEffectiveStatus != null
                || previousLatePenaltyApplied != null) {
            throw new IllegalArgumentException(
                    "Previous attendance snapshot is only allowed "
                            + "for correction events"
            );
        }

        if (eventType
                == StudentAttendanceNotificationEventType
                .ATTENDANCE_ALERT_CLEARED
                && eventSource
                != StudentAttendanceNotificationEventSource
                .CORRECTION_APPROVAL) {
            throw new IllegalArgumentException(
                    "Attendance alert clearing requires a correction"
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

    private static void requireNonNull(
            Object value,
            String message
    ) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateCredit(
            BigDecimal value,
            String fieldName
    ) {
        if (value == null
                || value.signum() < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and 1"
            );
        }
    }
}
