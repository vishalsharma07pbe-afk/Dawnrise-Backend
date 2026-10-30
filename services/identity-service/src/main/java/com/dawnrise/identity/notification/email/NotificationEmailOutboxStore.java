package com.dawnrise.identity.notification.email;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationEmailOutboxStore {
    private final JdbcClient jdbc;

    public NotificationEmailOutboxStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc.getJdbcTemplate() == null ? null : JdbcClient.create(jdbc.getJdbcTemplate());
    }

    public record Item(UUID id,long organizationId,UUID eventId,long userId,String email,String templateKey,
                       String subject,String variables,String actionType,Long actionReferenceId,String mode,
                       String messageId,int attemptCount) {}
    public record DeadLetter(UUID id,long userId,String templateKey,int attemptCount,String lastError,
                             OffsetDateTime deadLetteredAt) {}

    @Transactional
    public List<Item> claim(UUID owner,OffsetDateTime now,Duration lease,int limit) {
        return jdbc.sql("""
                WITH candidates AS (
                    SELECT id FROM notification_email_outbox
                     WHERE status='PENDING' AND available_at<=:now
                       AND (lease_until IS NULL OR lease_until<:now)
                     ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT :limit)
                UPDATE notification_email_outbox outbox
                   SET lease_owner=:owner,lease_until=:leaseUntil,attempt_count=attempt_count+1,
                       last_error=NULL,updated_at=:now
                  FROM candidates WHERE outbox.id=candidates.id
                RETURNING outbox.id,outbox.organization_id,outbox.event_id,outbox.user_id,
                          outbox.destination_email,outbox.template_key,outbox.subject,
                          outbox.template_variables::TEXT,outbox.action_type,outbox.action_reference_id,
                          outbox.delivery_mode,outbox.message_id,outbox.attempt_count
                """).param("now",now).param("owner",owner).param("leaseUntil",now.plus(lease))
                .param("limit",limit).query((rs,n)->new Item(rs.getObject(1,UUID.class),rs.getLong(2),
                        rs.getObject(3,UUID.class),rs.getLong(4),rs.getString(5),rs.getString(6),
                        rs.getString(7),rs.getString(8),rs.getString(9),(Long)rs.getObject(10),
                        rs.getString(11),rs.getString(12),rs.getInt(13))).list();
    }

    @Transactional
    public void sent(List<Item> items,UUID owner,OffsetDateTime now) {
        for(Item item:items) requireOne(jdbc.sql("UPDATE notification_email_outbox SET status='SENT',sent_at=:now,"
                        +"lease_owner=NULL,lease_until=NULL,last_error=NULL,updated_at=:now "
                        +"WHERE id=:id AND status='PENDING' AND lease_owner=:owner")
                .param("now",now).param("id",item.id()).param("owner",owner).update());
    }

    @Transactional
    public void failed(List<Item> items,UUID owner,OffsetDateTime now,int maximumAttempts,Duration baseBackoff,
                       Throwable failure) {
        for(Item item:items) {
            boolean dead=item.attemptCount()>=maximumAttempts;
            long multiplier=1L<<Math.min(Math.max(item.attemptCount()-1,0),6);
            String error=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();
            if(error.length()>1000) error=error.substring(0,1000);
            requireOne(jdbc.sql("UPDATE notification_email_outbox SET status=:status,available_at=:available,"
                            +"dead_lettered_at=:dead,last_error=:error,lease_owner=NULL,lease_until=NULL,updated_at=:now "
                            +"WHERE id=:id AND status='PENDING' AND lease_owner=:owner")
                    .param("status",dead?"DEAD_LETTER":"PENDING")
                    .param("available",now.plus(baseBackoff.multipliedBy(multiplier)))
                    .param("dead",dead?now:null).param("error",error).param("now",now)
                    .param("id",item.id()).param("owner",owner).update());
        }
    }

    public List<DeadLetter> deadLetters(long organizationId) {
        return jdbc.sql("SELECT id,user_id,template_key,attempt_count,last_error,dead_lettered_at "
                        +"FROM notification_email_outbox WHERE organization_id=:org AND status='DEAD_LETTER' "
                        +"ORDER BY dead_lettered_at DESC,id")
                .param("org",organizationId).query((rs,n)->new DeadLetter(rs.getObject(1,UUID.class),rs.getLong(2),
                        rs.getString(3),rs.getInt(4),rs.getString(5),rs.getObject(6,OffsetDateTime.class))).list();
    }

    @Transactional
    public boolean requeue(long organizationId,long actorUserId,UUID id,OffsetDateTime now) {
        boolean changed=jdbc.sql("UPDATE notification_email_outbox SET status='PENDING',attempt_count=0,"
                        +"available_at=:now,dead_lettered_at=NULL,last_error=NULL,lease_owner=NULL,lease_until=NULL,"
                        +"updated_at=:now WHERE id=:id AND organization_id=:org AND status='DEAD_LETTER'")
                .param("now",now).param("id",id).param("org",organizationId).update()==1;
        if(changed) jdbc.sql("INSERT INTO notification_email_delivery_audit"
                        +"(organization_id,outbox_id,action,actor_user_id,occurred_at) "
                        +"VALUES(:org,:id,'DEAD_LETTER_REQUEUED',:actor,:now)")
                .param("org",organizationId).param("id",id).param("actor",actorUserId).param("now",now).update();
        return changed;
    }

    private void requireOne(int changed) { if(changed!=1) throw new IllegalStateException("Email outbox lease was lost"); }
}
