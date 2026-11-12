package com.dawnrise.identity.notification.dto;

import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationEventType;
import com.dawnrise.identity.notification.model.NotificationImportance;
import com.dawnrise.identity.notification.model.NotificationDeliveryChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Set;

public record IngestNotificationRequest(
        @Positive long organizationId,
        @NotBlank @Size(max = 160) String sourceEventId,
        @NotNull NotificationEventType eventType,
        @NotNull NotificationCategory category,
        @NotNull NotificationImportance importance,
        @NotBlank @Size(max = 160) String heading,
        @NotBlank @Size(max = 500) String context,
        @Valid NotificationAction action,
        @Size(max = 160) String groupingKey,
        @PastOrPresent OffsetDateTime occurredAt,
        @NotEmpty @Size(max = 5000) Set<@Positive Long> recipientUserIds,
        @NotEmpty Set<@NotNull NotificationDeliveryChannel> deliveryChannels
) {
    public IngestNotificationRequest(long organizationId, String sourceEventId,
            NotificationEventType eventType, NotificationCategory category,
            NotificationImportance importance, String heading, String context,
            NotificationAction action, String groupingKey, OffsetDateTime occurredAt,
            Set<Long> recipientUserIds) {
        this(organizationId, sourceEventId, eventType, category, importance, heading, context,
                action, groupingKey, occurredAt, recipientUserIds,
                Set.of(NotificationDeliveryChannel.APP, NotificationDeliveryChannel.EMAIL));
    }
}
