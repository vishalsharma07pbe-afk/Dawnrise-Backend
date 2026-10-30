package com.dawnrise.identity.notification.dto;

import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationImportance;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationItemResponse(
        UUID id, String eventType, NotificationCategory category,
        NotificationImportance importance, String heading, String context,
        NotificationAction action, String groupingKey, OffsetDateTime occurredAt,
        OffsetDateTime createdAt, OffsetDateTime readAt, boolean hiddenFromNavbar
) {}
