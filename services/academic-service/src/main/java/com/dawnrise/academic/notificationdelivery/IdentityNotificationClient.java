package com.dawnrise.academic.notificationdelivery;

import java.time.OffsetDateTime;
import java.util.Set;

public interface IdentityNotificationClient {
    void ingest(NotificationRequest request);
    void resolveGroup(long organizationId, String groupingKey, String excludeSourceEventId);
    Set<Long> resolveRoles(long organizationId, Set<String> roles);

    record NotificationRequest(long organizationId, String sourceEventId, String eventType,
                               String category, String importance, String heading, String context,
                               Action action, String groupingKey, OffsetDateTime occurredAt,
                               Set<Long> recipientUserIds, Set<String> deliveryChannels) {
        NotificationRequest(long organizationId, String sourceEventId, String eventType,
                            String category, String importance, String heading, String context,
                            Action action, String groupingKey, OffsetDateTime occurredAt,
                            Set<Long> recipientUserIds) {
            this(organizationId, sourceEventId, eventType, category, importance, heading, context,
                    action, groupingKey, occurredAt, recipientUserIds, Set.of("APP", "EMAIL"));
        }
    }

    record Action(String type, Long referenceId, Long studentUserId, Long academicYearId) {
    }
}
