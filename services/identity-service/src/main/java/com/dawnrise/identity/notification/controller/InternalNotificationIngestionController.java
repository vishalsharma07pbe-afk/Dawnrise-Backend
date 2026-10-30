package com.dawnrise.identity.notification.controller;

import com.dawnrise.identity.notification.dto.IngestNotificationRequest;
import com.dawnrise.identity.notification.dto.IngestNotificationResponse;
import com.dawnrise.identity.notification.dto.ResolveNotificationGroupRequest;
import com.dawnrise.identity.notification.dto.ResolveNotificationGroupResponse;
import com.dawnrise.identity.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/notification-events")
public class InternalNotificationIngestionController {
    private final NotificationService notifications;

    public InternalNotificationIngestionController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public IngestNotificationResponse ingest(Authentication authentication,
                                               @Valid @RequestBody IngestNotificationRequest request) {
        return notifications.ingest(authentication.getName(), request);
    }

    @PostMapping("/resolve-group")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResolveNotificationGroupResponse resolveGroup(
            Authentication authentication,
            @Valid @RequestBody ResolveNotificationGroupRequest request) {
        return notifications.resolveGroup(authentication.getName(), request.organizationId(),
                request.groupingKey(), request.excludeSourceEventId());
    }
}
