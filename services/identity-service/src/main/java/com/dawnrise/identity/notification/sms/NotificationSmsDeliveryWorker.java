package com.dawnrise.identity.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "dawnrise.notification-sms", name = "enabled", havingValue = "true")
public class NotificationSmsDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationSmsDeliveryWorker.class);
    private final NotificationSmsOutboxStore store;
    private final Msg91SmsClient client;
    private final NotificationSmsProperties properties;
    private final ObjectMapper json;

    public NotificationSmsDeliveryWorker(NotificationSmsOutboxStore store, Msg91SmsClient client,
                                         NotificationSmsProperties properties, ObjectMapper json) {
        this.store = store; this.client = client; this.properties = properties; this.json = json;
    }

    @Scheduled(fixedDelayString = "${dawnrise.notification-sms.delay:PT30S}",
            initialDelayString = "${dawnrise.notification-sms.initial-delay:PT30S}")
    public void deliver() {
        if (!properties.configured()) return;
        UUID owner = UUID.randomUUID(); OffsetDateTime now = OffsetDateTime.now();
        for (var item : store.claim(owner, now, Duration.ofMinutes(2), 50)) {
            try {
                String templateId = properties.templateId(item.templateKey());
                if (templateId == null || templateId.isBlank()) throw new IllegalStateException("SMS template is not configured");
                Map<String, String> variables = json.readValue(item.variables(), new TypeReference<>() {});
                store.accepted(item, owner, client.send(templateId, item.phone(), variables), OffsetDateTime.now());
            } catch (Exception failure) {
                log.warn("Notification SMS delivery failed for outbox item {}", item.id(), failure);
                store.failed(item, owner, OffsetDateTime.now(), properties.getMaximumAttempts(), failure);
            }
        }
    }
}
