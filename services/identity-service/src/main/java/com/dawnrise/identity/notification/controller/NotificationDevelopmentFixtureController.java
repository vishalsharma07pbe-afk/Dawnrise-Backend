package com.dawnrise.identity.notification.controller;

import com.dawnrise.identity.notification.dto.IngestNotificationRequest;
import com.dawnrise.identity.notification.dto.IngestNotificationResponse;
import com.dawnrise.identity.notification.model.NotificationEventType;
import com.dawnrise.identity.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/notification-development-fixtures")
@ConditionalOnProperty(name = "notifications.development-fixtures-enabled", havingValue = "true")
public class NotificationDevelopmentFixtureController {
    private final NotificationService notifications;

    public NotificationDevelopmentFixtureController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public IngestNotificationResponse create(@Valid @RequestBody IngestNotificationRequest request) {
        if (request.eventType() != NotificationEventType.DEVELOPMENT_SAMPLE) {
            throw new IllegalArgumentException("Development fixtures require DEVELOPMENT_SAMPLE");
        }
        return notifications.ingest("development-fixture", request);
    }
}
