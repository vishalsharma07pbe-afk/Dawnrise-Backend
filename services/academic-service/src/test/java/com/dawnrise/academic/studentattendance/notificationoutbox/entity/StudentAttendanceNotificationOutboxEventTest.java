package com.dawnrise.academic.studentattendance.notificationoutbox.entity;

import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventSource;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventType;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationPublicationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceNotificationOutboxEventTest {

    @Test
    void createsPendingSubmissionEvent() {
        OffsetDateTime occurredAt =
                OffsetDateTime.parse("2026-09-12T08:00:00Z");

        StudentAttendanceNotificationOutboxEvent event =
                event(
                        StudentAttendanceNotificationEventSource
                                .MANUAL_SUBMISSION,
                        null,
                        occurredAt
                );

        assertThat(event.getPublicationStatus())
                .isEqualTo(StudentAttendanceNotificationPublicationStatus.PENDING);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getAvailableAt()).isEqualTo(occurredAt);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getCorrectionRequestId()).isNull();
    }

    @Test
    void enforcesCorrectionSourceConsistencyAndRequiredFields() {
        assertThatThrownBy(() -> event(
                StudentAttendanceNotificationEventSource.CORRECTION_APPROVAL,
                null,
                OffsetDateTime.parse("2026-09-12T08:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event(
                StudentAttendanceNotificationEventSource.MANUAL_SUBMISSION,
                99L,
                OffsetDateTime.parse("2026-09-12T08:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentAttendanceNotificationOutboxEvent(
                null,
                1L, 2L, 3L, 4L, 5L, 6L, null, 7L, 8L,
                LocalDate.of(2026, 9, 12),
                StudentAttendanceNotificationEventType.ABSENCE_RECORDED,
                StudentAttendanceNotificationEventSource.MANUAL_SUBMISSION,
                "key",
                "{}",
                OffsetDateTime.parse("2026-09-12T08:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static StudentAttendanceNotificationOutboxEvent event(
            StudentAttendanceNotificationEventSource source,
            Long correctionRequestId,
            OffsetDateTime occurredAt
    ) {
        return new StudentAttendanceNotificationOutboxEvent(
                UUID.randomUUID(),
                1L, 2L, 3L, 4L, 5L, 6L, correctionRequestId, 7L, 8L,
                LocalDate.of(2026, 9, 12),
                StudentAttendanceNotificationEventType.ABSENCE_RECORDED,
                source,
                "student-attendance:key",
                "{\"schemaVersion\":1}",
                occurredAt
        );
    }
}
