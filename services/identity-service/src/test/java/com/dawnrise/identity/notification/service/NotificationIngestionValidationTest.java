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
