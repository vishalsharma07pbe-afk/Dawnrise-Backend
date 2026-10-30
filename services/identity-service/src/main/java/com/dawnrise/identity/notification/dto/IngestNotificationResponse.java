package com.dawnrise.identity.notification.dto;

import java.util.UUID;

public record IngestNotificationResponse(UUID eventId, boolean created, int recipientCount) {}
