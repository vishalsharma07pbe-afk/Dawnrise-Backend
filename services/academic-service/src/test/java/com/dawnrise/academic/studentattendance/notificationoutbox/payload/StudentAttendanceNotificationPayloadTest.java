package com.dawnrise.academic.studentattendance.notificationoutbox.payload;

import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventSource;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventType;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceNotificationPayloadTest {

    @Test
    void acceptsSubmissionPayloadWithoutPreviousSnapshot() {
        StudentAttendanceNotificationPayload payload = submissionPayload();

        assertThat(payload.schemaVersion()).isEqualTo(1);
        assertThat(payload.correctionRequestId()).isNull();
        assertThat(payload.previousRecordedStatus()).isNull();
    }

    @Test
    void correctionPayloadRequiresPreviousSnapshot() {
        StudentAttendanceNotificationPayload payload =
                correctionPayload(
                        StudentAttendanceNotificationEventType
                                .ATTENDANCE_ALERT_CLEARED
                );

        assertThat(payload.correctionRequestId()).isEqualTo(30L);
        assertThat(payload.previousRecordedStatus())
                .isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void rejectsInvalidSchemaCreditsAndCorrectionConsistency() {
        assertThatThrownBy(() -> new StudentAttendanceNotificationPayload(
                2,
                StudentAttendanceNotificationEventType.ABSENCE_RECORDED,
                StudentAttendanceNotificationEventSource.MANUAL_SUBMISSION,
                1L, 2L, 3L, 4L, 5L, 6L, null, 7L, 8L,
                LocalDate.of(2026, 9, 12),
                null, null, null,
                AttendanceStatus.ABSENT,
                AttendanceStatus.ABSENT,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                false,
                StudentAttendanceSubmissionType.MANUAL,
                OffsetDateTime.parse("2026-09-12T08:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> correctionPayload(
                StudentAttendanceNotificationEventType.ABSENCE_RECORDED,
                null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentAttendanceNotificationPayload(
                1,
                StudentAttendanceNotificationEventType.ATTENDANCE_ALERT_CLEARED,
                StudentAttendanceNotificationEventSource.MANUAL_SUBMISSION,
                1L, 2L, 3L, 4L, 5L, 6L, null, 7L, 8L,
                LocalDate.of(2026, 9, 12),
                null, null, null,
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                false,
                StudentAttendanceSubmissionType.MANUAL,
                OffsetDateTime.parse("2026-09-12T08:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static StudentAttendanceNotificationPayload submissionPayload() {
        return new StudentAttendanceNotificationPayload(
                1,
                StudentAttendanceNotificationEventType.ABSENCE_RECORDED,
                StudentAttendanceNotificationEventSource.MANUAL_SUBMISSION,
                1L, 2L, 3L, 4L, 5L, 6L, null, 7L, 8L,
                LocalDate.of(2026, 9, 12),
                null, null, null,
                AttendanceStatus.ABSENT,
                AttendanceStatus.ABSENT,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                false,
                StudentAttendanceSubmissionType.MANUAL,
                OffsetDateTime.parse("2026-09-12T08:00:00Z")
        );
    }

    private static StudentAttendanceNotificationPayload correctionPayload(
            StudentAttendanceNotificationEventType eventType
    ) {
        return correctionPayload(eventType, AttendanceStatus.ABSENT);
    }

    private static StudentAttendanceNotificationPayload correctionPayload(
            StudentAttendanceNotificationEventType eventType,
            AttendanceStatus previousRecordedStatus
    ) {
        return new StudentAttendanceNotificationPayload(
                1,
                eventType,
                StudentAttendanceNotificationEventSource.CORRECTION_APPROVAL,
                1L, 2L, 3L, 4L, 5L, 6L, 30L, 7L, 8L,
                LocalDate.of(2026, 9, 12),
                previousRecordedStatus,
                AttendanceStatus.ABSENT,
                false,
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                BigDecimal.ONE,
                BigDecimal.ONE,
                false,
                StudentAttendanceSubmissionType.MANUAL,
                OffsetDateTime.parse("2026-09-12T08:00:00Z")
        );
    }
}
