package com.dawnrise.identity.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="dawnrise.notification-email.enabled",havingValue="true",matchIfMissing=true)
public class NotificationEmailDeliveryWorker {
    private static final Logger log=LoggerFactory.getLogger(NotificationEmailDeliveryWorker.class);
    private final NotificationEmailOutboxStore store;
    private final JavaMailSender mail;

    public NotificationEmailDeliveryWorker(NotificationEmailOutboxStore store,JavaMailSender mail) {
        this.store=store; this.mail=mail;
    }

    @Scheduled(fixedDelayString="${dawnrise.notification-email.delay:PT30S}",
            initialDelayString="${dawnrise.notification-email.initial-delay:PT30S}")
    public void deliver() {
        UUID owner=UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now();
        List<NotificationEmailOutboxStore.Item> items=store.claim(owner,now,Duration.ofMinutes(2),50);
        Map<String,List<NotificationEmailOutboxStore.Item>> batches=new LinkedHashMap<>();
        for(var item:items) {
            String key="DIGEST".equals(item.mode())?item.organizationId()+":"+item.userId()+":"+item.email():item.id().toString();
            batches.computeIfAbsent(key,ignored->new ArrayList<>()).add(item);
        }
        for(var batch:batches.values()) send(batch,owner);
    }

    void send(List<NotificationEmailOutboxStore.Item> items,UUID owner) {
        try {
            var first=items.getFirst(); SimpleMailMessage message=new SimpleMailMessage();
            message.setTo(first.email());
            message.setSubject(items.size()==1?first.subject():"Your Dawnrise notification digest");
            StringBuilder body=new StringBuilder("Sign in to Dawnrise to review these updates:\n\n");
            for(var item:items) body.append("• ").append(item.subject()).append("\n");
            body.append("\nFor privacy and security, details are available only after signing in.\n");
            // SimpleMailMessage cannot set a Message-ID header. The durable message_id still prevents
            // application retries from creating a second job; SMTP may duplicate after an ambiguous send result.
            mail.send(message);
            store.sent(items,owner,OffsetDateTime.now());
        } catch(RuntimeException failure) {
            log.warn("Notification email delivery failed for outbox batch",failure);
            store.failed(items,owner,OffsetDateTime.now(),5,Duration.ofMinutes(1),failure);
        }
    }
}
