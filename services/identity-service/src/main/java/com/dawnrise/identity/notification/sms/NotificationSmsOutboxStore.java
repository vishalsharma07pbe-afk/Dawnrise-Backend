package com.dawnrise.identity.notification.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "dawnrise.notification-sms", name = "enabled", havingValue = "true")
public class NotificationSmsOutboxStore {
    private final JdbcClient jdbc;
    public NotificationSmsOutboxStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = JdbcClient.create(jdbc.getJdbcTemplate()); }

    public record Item(UUID id, String phone, String templateKey, String variables, int attemptCount) {}

    @Transactional
    public List<Item> claim(UUID owner, OffsetDateTime now, Duration lease, int limit) {
        return jdbc.sql("""
                WITH candidates AS (
                    SELECT id FROM notification_sms_outbox
                     WHERE status='PENDING' AND available_at<=:now
                       AND (lease_until IS NULL OR lease_until<:now)
                     ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT :limit)
                UPDATE notification_sms_outbox outbox
                   SET lease_owner=:owner,lease_until=:leaseUntil,attempt_count=attempt_count+1,
                       last_error=NULL,updated_at=:now
                  FROM candidates WHERE outbox.id=candidates.id
                RETURNING outbox.id,outbox.destination_phone,outbox.template_key,
                          outbox.template_variables::TEXT,outbox.attempt_count
                """).param("now", now).param("owner", owner).param("leaseUntil", now.plus(lease))
                .param("limit", limit).query((rs, n) -> new Item(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5))).list();
    }

    @Transactional
    public void accepted(Item item, UUID owner, String providerId, OffsetDateTime now) {
        requireOne(jdbc.sql("UPDATE notification_sms_outbox SET status='ACCEPTED',provider_message_id=:providerId,"
                        + "accepted_at=:now,lease_owner=NULL,lease_until=NULL,last_error=NULL,updated_at=:now "
                        + "WHERE id=:id AND status='PENDING' AND lease_owner=:owner")
                .param("providerId", providerId).param("now", now).param("id", item.id())
                .param("owner", owner).update());
    }

    @Transactional
    public void failed(Item item, UUID owner, OffsetDateTime now, int maximumAttempts, Throwable failure) {
        boolean dead = item.attemptCount() >= maximumAttempts;
        long minutes = 1L << Math.min(Math.max(item.attemptCount() - 1, 0), 6);
        String error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (error.length() > 1000) error = error.substring(0, 1000);
        requireOne(jdbc.sql("UPDATE notification_sms_outbox SET status=:status,available_at=:available,"
                        + "dead_lettered_at=:dead,last_error=:error,lease_owner=NULL,lease_until=NULL,updated_at=:now "
                        + "WHERE id=:id AND status='PENDING' AND lease_owner=:owner")
                .param("status", dead ? "DEAD_LETTER" : "PENDING").param("available", now.plusMinutes(minutes))
                .param("dead", dead ? now : null).param("error", error).param("now", now)
                .param("id", item.id()).param("owner", owner).update());
    }

    private void requireOne(int changed) { if (changed != 1) throw new IllegalStateException("SMS outbox lease was lost"); }
}
