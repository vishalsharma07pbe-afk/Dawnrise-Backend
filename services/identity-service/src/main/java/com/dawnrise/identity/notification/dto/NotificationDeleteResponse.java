package com.dawnrise.identity.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationDeleteResponse(UUID notificationId, OffsetDateTime undoUntil) {}
