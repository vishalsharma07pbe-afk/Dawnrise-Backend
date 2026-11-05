package com.dawnrise.identity.notification.service;

import com.dawnrise.identity.notification.dto.IngestNotificationRequest;
import com.dawnrise.identity.notification.dto.NotificationAction;
import com.dawnrise.identity.notification.model.NotificationActionType;
import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationEventType;
import com.dawnrise.identity.notification.model.NotificationImportance;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class NotificationIngestionValidationTest {
    private final NotificationServiceImpl service = new NotificationServiceImpl(
            mock(NamedParameterJdbcTemplate.class), mock(UserRepository.class));

    @Test
    void rejectsEventTypesNotOwnedByAuthenticatedSource() {
        assertThrows(IllegalArgumentException.class,
                () -> service.ingest("school-service", validRequest()));
    }

    @Test
    void acceptsStaffLeaveEventsFromSchoolService() {
        IngestNotificationRequest request = new IngestNotificationRequest(
                7L, "leave_requested:3:1", NotificationEventType.LEAVE_REQUESTED,
                NotificationCategory.LEAVE, NotificationImportance.NORMAL,
                "Leave request submitted", "Your leave request was submitted",
                new NotificationAction(NotificationActionType.LEAVE_REQUEST, 3L, null, null),
                "leave:3", OffsetDateTime.now(), Set.of(12L));

        assertDoesNotThrow(() -> service.validateIngestion("school-service", request));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateIngestion("academic-service", request));
    }

    @Test
    void rejectsMarkupBeforeAnyPersistence() {
        IngestNotificationRequest request = new IngestNotificationRequest(
                1L, "event-1", NotificationEventType.COVER_NEEDED,
                NotificationCategory.TIMETABLE, NotificationImportance.IMPORTANT,
                "<b>Cover needed</b>", "Grade 1 Section A",
                new NotificationAction(NotificationActionType.DAILY_COVERAGE, 5L, null, 2L),
                "cover:5", OffsetDateTime.now(), Set.of(10L));
        assertThrows(IllegalArgumentException.class,
                () -> service.ingest("academic-service", request));
    }

    @Test
    void rejectsAnActionThatDoesNotMatchTheAllowlistedEventType() {
        IngestNotificationRequest request = new IngestNotificationRequest(
                1L, "event-1", NotificationEventType.COVER_NEEDED,
                NotificationCategory.TIMETABLE, NotificationImportance.IMPORTANT,
                "Cover needed", "Grade 1 Section A",
                new NotificationAction(NotificationActionType.SETTINGS, null, null, null),
                "cover:5", OffsetDateTime.now(), Set.of(10L));
        assertThrows(IllegalArgumentException.class,
                () -> service.ingest("academic-service", request));
    }

    private IngestNotificationRequest validRequest() {
        return new IngestNotificationRequest(1L, "event-1",
                NotificationEventType.COVER_NEEDED, NotificationCategory.TIMETABLE,
                NotificationImportance.IMPORTANT, "Cover needed", "Grade 1 Section A",
                new NotificationAction(NotificationActionType.DAILY_COVERAGE, 5L, null, 2L),
                "cover:5", OffsetDateTime.now(), Set.of(10L));
    }
}
