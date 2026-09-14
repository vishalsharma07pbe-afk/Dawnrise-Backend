package com.dawnrise.academic.studentattendance.notificationoutbox.service.impl;

import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionItem;
import com.dawnrise.academic.studentattendance.notificationoutbox.entity.StudentAttendanceNotificationOutboxEvent;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventSource;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventType;
import com.dawnrise.academic.studentattendance.notificationoutbox.repository.StudentAttendanceNotificationOutboxRepository;
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationClassifier;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudentAttendanceNotificationOutboxServiceImplTest {

    private StudentAttendanceNotificationOutboxRepository repository;
    private ObjectMapper objectMapper;
    private StudentAttendanceNotificationOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(StudentAttendanceNotificationOutboxRepository.class);
        objectMapper = spy(new ObjectMapper());
        service = new StudentAttendanceNotificationOutboxServiceImpl(
                repository,
                new StudentAttendanceNotificationClassifier(),
                objectMapper
        );
    }

    @Test
    void submittedSessionCreatesAbsenceLateAndPenaltyEventsOnly()
            throws Exception {
        StudentAttendanceSession session = submittedManualSession();
        StudentAttendanceRecord absent =
                record(session, 10L, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, false);
        StudentAttendanceRecord late =
                record(session, 11L, AttendanceStatus.LATE,
                        AttendanceStatus.LATE, false);
        StudentAttendanceRecord penalized =
                record(session, 12L, AttendanceStatus.LATE,
                        AttendanceStatus.ABSENT, true);
        StudentAttendanceRecord present =
                record(session, 13L, AttendanceStatus.PRESENT,
                        AttendanceStatus.PRESENT, false);

        service.createForSubmittedSession(
                session,
                List.of(absent, late, penalized, present)
        );

        List<StudentAttendanceNotificationOutboxEvent> events =
                capturedEvents();
        assertThat(events)
                .extracting(StudentAttendanceNotificationOutboxEvent::getEventType)
                .containsExactly(
                        StudentAttendanceNotificationEventType.ABSENCE_RECORDED,
                        StudentAttendanceNotificationEventType.LATE_RECORDED,
                        StudentAttendanceNotificationEventType.LATE_PENALTY_APPLIED
                );
        assertThat(events)
                .extracting(StudentAttendanceNotificationOutboxEvent::getEventSource)
                .containsOnly(StudentAttendanceNotificationEventSource.MANUAL_SUBMISSION);
        assertThat(events.get(2).getIdempotencyKey())
                .contains("event:LATE_PENALTY_APPLIED");
        assertThat(events.get(2).getPayload())
                .contains("\"eventType\":\"LATE_PENALTY_APPLIED\"")
                .contains("\"eventSource\":\"MANUAL_SUBMISSION\"");
    }

    @Test
    void submittedAutomaticSessionUsesAutomaticSource() {
        StudentAttendanceSession session = submittedAutomaticSession();
        StudentAttendanceRecord absent =
                record(session, 10L, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, false);

        service.createForSubmittedSession(session, List.of(absent));

        StudentAttendanceNotificationOutboxEvent event =
                capturedEvents().getFirst();
        assertThat(event.getEventSource())
                .isEqualTo(StudentAttendanceNotificationEventSource.AUTOMATIC_SUBMISSION);
        assertThat(event.getOccurredAt())
                .isEqualTo(session.getSubmittedAt());
    }

    @Test
    void idempotencySkipsDuplicateRows() {
        StudentAttendanceSession session = submittedManualSession();
        StudentAttendanceRecord absent =
                record(session, 10L, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, false);
        when(repository.existsByOrganizationIdAndIdempotencyKey(
                eq(1L),
                any()
        )).thenReturn(true);

        service.createForSubmittedSession(session, List.of(absent));

        verify(repository, never()).save(any());
    }

    @Test
    void serializationFailurePropagatesToRollBackAttendanceTransaction()
            throws Exception {
        StudentAttendanceSession session = submittedManualSession();
        StudentAttendanceRecord absent =
                record(session, 10L, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, false);
        doThrow(new JacksonException("cannot serialize") {
        }).when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() ->
                service.createForSubmittedSession(session, List.of(absent))
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Attendance notification event could not be serialized"
                );
        verify(repository, never()).save(any());
    }

    @Test
    void approvedCorrectionToAlertableStateEmitsNewAlert() {
        StudentAttendanceSession session = submittedManualSession();
        StudentAttendanceRecord before =
                record(session, 10L, AttendanceStatus.PRESENT,
                        AttendanceStatus.PRESENT, false);
        StudentAttendanceCorrectionItem item =
                correctionItem(30L, before, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, BigDecimal.ZERO, false);
        StudentAttendanceRecord corrected =
                record(session, 10L, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, false);

        service.createForApprovedCorrection(
                session,
                30L,
                List.of(item),
                Map.of(corrected.getId(), corrected),
                OffsetDateTime.parse("2026-09-12T09:00:00Z")
        );

        StudentAttendanceNotificationOutboxEvent event =
                capturedEvents().getFirst();
        assertThat(event.getEventType())
                .isEqualTo(StudentAttendanceNotificationEventType.ABSENCE_RECORDED);
        assertThat(event.getEventSource())
                .isEqualTo(StudentAttendanceNotificationEventSource.CORRECTION_APPROVAL);
        assertThat(event.getCorrectionRequestId()).isEqualTo(30L);
        assertThat(event.getPayload())
                .contains("\"previousRecordedStatus\":\"PRESENT\"")
                .contains("\"recordedStatus\":\"ABSENT\"");
    }

    @Test
    void approvedCorrectionFromAlertableToNonAlertableEmitsClearedAlert() {
        StudentAttendanceSession session = submittedManualSession();
        StudentAttendanceRecord before =
                record(session, 10L, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, false);
        StudentAttendanceCorrectionItem item =
                correctionItem(30L, before, AttendanceStatus.PRESENT,
                        AttendanceStatus.PRESENT, BigDecimal.ONE, false);
        StudentAttendanceRecord corrected =
                record(session, 10L, AttendanceStatus.PRESENT,
                        AttendanceStatus.PRESENT, false);

        service.createForApprovedCorrection(
                session,
                30L,
                List.of(item),
                Map.of(corrected.getId(), corrected),
                OffsetDateTime.parse("2026-09-12T09:00:00Z")
        );

        assertThat(capturedEvents().getFirst().getEventType())
                .isEqualTo(StudentAttendanceNotificationEventType
                        .ATTENDANCE_ALERT_CLEARED);
    }

    @Test
    void remarksOnlyOrCreditOnlyCorrectionsDoNotEmitEvents() {
        StudentAttendanceSession session = submittedManualSession();
        StudentAttendanceRecord before =
                record(session, 10L, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, false);
        StudentAttendanceCorrectionItem remarksOnly =
                correctionItem(30L, before, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, BigDecimal.ZERO, false);
        StudentAttendanceRecord corrected =
                record(session, 10L, AttendanceStatus.ABSENT,
                        AttendanceStatus.ABSENT, false);

        service.createForApprovedCorrection(
                session,
                30L,
                List.of(remarksOnly),
                Map.of(corrected.getId(), corrected),
                OffsetDateTime.parse("2026-09-12T09:00:00Z")
        );

        verify(repository, never()).save(any());
    }

    private List<StudentAttendanceNotificationOutboxEvent> capturedEvents() {
        ArgumentCaptor<StudentAttendanceNotificationOutboxEvent> captor =
                ArgumentCaptor.forClass(
                        StudentAttendanceNotificationOutboxEvent.class
                );
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static StudentAttendanceSession submittedManualSession() {
        StudentAttendanceSession session = session();
        session.submitManually(90L);
        return session;
    }

    private static StudentAttendanceSession submittedAutomaticSession() {
        StudentAttendanceSession session = session();
        session.submitAutomatically(
                OffsetDateTime.parse("2026-09-12T08:30:00Z")
        );
        return session;
    }

    private static StudentAttendanceSession session() {
        StudentAttendanceSession session =
                new StudentAttendanceSession(
                        1L,
                        2L,
                        3L,
                        4L,
                        5L,
                        LocalDate.of(2026, 9, 12),
                        90L
                );
        ReflectionTestUtils.setField(session, "id", 6L);
        return session;
    }

    private static StudentAttendanceRecord record(
            StudentAttendanceSession session,
            long enrollmentId,
            AttendanceStatus recordedStatus,
            AttendanceStatus effectiveStatus,
            boolean latePenaltyApplied
    ) {
        BigDecimal earnedCredit =
                effectiveStatus == AttendanceStatus.ABSENT
                        ? BigDecimal.ZERO
                        : BigDecimal.ONE;
        StudentAttendanceRecord record =
                new StudentAttendanceRecord(
                        session,
                        enrollmentId,
                        enrollmentId + 100L,
                        recordedStatus,
                        effectiveStatus,
                        earnedCredit,
                        BigDecimal.ONE,
                        latePenaltyApplied,
                        null,
                        90L
                );
        ReflectionTestUtils.setField(record, "id", enrollmentId + 1000L);
        ReflectionTestUtils.setField(record, "version", 1L);
        return record;
    }

    private static StudentAttendanceCorrectionItem correctionItem(
            long correctionRequestId,
            StudentAttendanceRecord before,
            AttendanceStatus proposedRecordedStatus,
            AttendanceStatus proposedEffectiveStatus,
            BigDecimal proposedEarnedCredit,
            boolean proposedLatePenaltyApplied
    ) {
        return new StudentAttendanceCorrectionItem(
                correctionRequestId,
                before,
                before.getVersion(),
                proposedRecordedStatus,
                proposedEffectiveStatus,
                proposedEarnedCredit,
                BigDecimal.ONE,
                proposedLatePenaltyApplied,
                "Corrected"
        );
    }
}
