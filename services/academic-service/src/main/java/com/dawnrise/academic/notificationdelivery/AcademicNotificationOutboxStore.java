package com.dawnrise.academic.notificationdelivery;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class AcademicNotificationOutboxStore {
    private final JdbcClient jdbc;

    public AcademicNotificationOutboxStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public enum Source { ATTENDANCE, DAILY_COVER, PUBLICATION }

    public record Item(Source source, long id, UUID eventId, long organizationId,
                       Long academicYearId, Long studentUserId, Long caseId, Long artifactId,
                       String eventType, String payload, OffsetDateTime occurredAt, int attemptCount,
                       Set<Long> recipientUserIds) {
    }

    public record CoverContext(long academicYearId, String sectionName,
                               java.time.LocalDate coverDate, java.time.LocalTime startTime,
                               Long absenceRecorder, Long proposer, Long approver,
                               Long resourceTeacher) {
        Set<Long> managementRecipients() {
            LinkedHashSet<Long> values = new LinkedHashSet<>();
            add(values, absenceRecorder);
            add(values, proposer);
            add(values, approver);
            return Set.copyOf(values);
        }

        Set<Long> confirmedRecipients() {
            LinkedHashSet<Long> values = new LinkedHashSet<>(managementRecipients());
            add(values, resourceTeacher);
            return Set.copyOf(values);
        }

        private static void add(Set<Long> values, Long id) {
            if (id != null && id > 0) values.add(id);
        }
    }

    public record DeadLetter(Source source, long id, UUID eventId, String eventType,
                             int attemptCount, String lastError, OffsetDateTime deadLetteredAt) {
    }

    @Transactional
    public List<Item> claim(Source source, UUID owner, OffsetDateTime now,
                            Duration leaseDuration, int limit) {
        return switch (source) {
            case ATTENDANCE -> claimAttendance(owner, now, leaseDuration, limit);
            case DAILY_COVER -> claimCover(owner, now, leaseDuration, limit);
            case PUBLICATION -> claimPublication(owner, now, leaseDuration, limit);
        };
    }

    private List<Item> claimAttendance(UUID owner, OffsetDateTime now, Duration lease, int limit) {
        return jdbc.sql("""
                WITH candidates AS (
                    SELECT id FROM student_attendance_notification_outbox
                     WHERE publication_status='PENDING' AND available_at<=:now
                       AND (lease_until IS NULL OR lease_until<:now)
                     ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT :limit
                )
                UPDATE student_attendance_notification_outbox o
                   SET lease_owner=:owner,lease_until=:leaseUntil,attempt_count=attempt_count+1,
                       last_error=NULL,updated_at=:now,version=version+1
                  FROM candidates c WHERE o.id=c.id
                RETURNING o.id,o.event_id,o.organization_id,o.academic_year_id,o.student_user_id,
                          NULL::BIGINT case_id,NULL::BIGINT artifact_id,o.event_type,o.payload::TEXT,
                          o.occurred_at,o.attempt_count,o.recipient_user_ids
                """).param("now", now).param("owner", owner).param("leaseUntil", now.plus(lease))
                .param("limit", limit).query((rs, n) -> item(Source.ATTENDANCE, rs)).list();
    }

    private List<Item> claimCover(UUID owner, OffsetDateTime now, Duration lease, int limit) {
        return jdbc.sql("""
                WITH candidates AS (
                    SELECT id FROM timetable_daily_cover_notification_outbox
                     WHERE publication_status='PENDING' AND available_at<=:now
                       AND (lease_until IS NULL OR lease_until<:now)
                     ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT :limit
                )
                UPDATE timetable_daily_cover_notification_outbox o
                   SET lease_owner=:owner,lease_until=:leaseUntil,attempt_count=attempt_count+1,
                       last_error=NULL,updated_at=:now
                  FROM candidates c WHERE o.id=c.id
                RETURNING o.id,o.event_id,o.organization_id,NULL::BIGINT academic_year_id,
                          NULL::BIGINT student_user_id,o.case_id,NULL::BIGINT artifact_id,
                          o.event_type,o.payload::TEXT,o.created_at occurred_at,o.attempt_count,
                          o.recipient_user_ids
                """).param("now", now).param("owner", owner).param("leaseUntil", now.plus(lease))
                .param("limit", limit).query((rs, n) -> item(Source.DAILY_COVER, rs)).list();
    }

    private List<Item> claimPublication(UUID owner, OffsetDateTime now, Duration lease, int limit) {
        return jdbc.sql("""
                WITH candidates AS (
                    SELECT id FROM academic_publication_notification_outbox
                     WHERE publication_status='PENDING' AND available_at<=:now
                       AND (lease_until IS NULL OR lease_until<:now)
                     ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT :limit
                )
                UPDATE academic_publication_notification_outbox o
                   SET lease_owner=:owner,lease_until=:leaseUntil,attempt_count=attempt_count+1,
                       last_error=NULL,updated_at=:now
                  FROM candidates c WHERE o.id=c.id
                RETURNING o.id,o.event_id,o.organization_id,o.academic_year_id,o.student_user_id,
                          NULL::BIGINT case_id,o.artifact_id,o.event_type,o.payload::TEXT,
                          o.occurred_at,o.attempt_count,o.recipient_user_ids
                """).param("now", now).param("owner", owner).param("leaseUntil", now.plus(lease))
                .param("limit", limit).query((rs, n) -> item(Source.PUBLICATION, rs)).list();
    }

    @Transactional
    public Set<Long> snapshotRecipients(Item item, Set<Long> recipients) {
        if (item.recipientUserIds() != null && !item.recipientUserIds().isEmpty()) {
            return item.recipientUserIds();
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new NotificationDeliveryException("Notification has no eligible recipients", null);
        }
        String table = table(item.source());
        String literal = recipients.stream().sorted().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        jdbc.sql("UPDATE " + table + " SET recipient_user_ids=CAST(:recipients AS BIGINT[]) "
                        + "WHERE id=:id AND recipient_user_ids IS NULL")
                .param("recipients", literal).param("id", item.id()).update();
        return jdbc.sql("SELECT recipient_user_ids FROM " + table + " WHERE id=:id")
                .param("id", item.id()).query((rs, n) -> array(rs.getArray(1))).single();
    }

    @Transactional
    public void markPublished(Item item, UUID owner, OffsetDateTime now) {
        int changed = jdbc.sql("UPDATE " + table(item.source()) + " SET publication_status='PUBLISHED',"
                        + "published_at=:now,lease_owner=NULL,lease_until=NULL,last_error=NULL,updated_at=:now "
                        + "WHERE id=:id AND publication_status='PENDING' AND lease_owner=:owner")
                .param("now", now).param("id", item.id()).param("owner", owner).update();
        if (changed != 1) throw new IllegalStateException("Notification outbox lease was lost");
    }

    @Transactional
    public void recordFailure(Item item, UUID owner, OffsetDateTime now, int maximumAttempts,
                              Duration baseBackoff, Throwable failure) {
        boolean dead = item.attemptCount() >= maximumAttempts;
        long multiplier = 1L << Math.min(Math.max(item.attemptCount() - 1, 0), 6);
        OffsetDateTime available = now.plus(baseBackoff.multipliedBy(multiplier));
        String error = safeError(failure);
        int changed = jdbc.sql("UPDATE " + table(item.source()) + " SET publication_status=:status,"
                        + "available_at=:available,dead_lettered_at=:deadAt,last_error=:error,"
                        + "lease_owner=NULL,lease_until=NULL,updated_at=:now "
                        + "WHERE id=:id AND publication_status='PENDING' AND lease_owner=:owner")
                .param("status", dead ? "DEAD_LETTER" : "PENDING")
                .param("available", available).param("deadAt", dead ? now : null)
                .param("error", error).param("now", now).param("id", item.id()).param("owner", owner).update();
        if (changed != 1) throw new IllegalStateException("Notification outbox lease was lost");
    }

    public CoverContext coverContext(long organizationId, long caseId) {
        return jdbc.sql("""
                SELECT c.academic_year_id,c.section_name,c.cover_date,c.start_time,a.recorded_by_user_id,
                       p.proposed_by_user_id,p.approved_by_user_id,p.resource_teacher_user_id
                  FROM timetable_daily_cover_cases c
                  JOIN timetable_teacher_absences a ON a.id=c.absence_id AND a.organization_id=c.organization_id
                  LEFT JOIN LATERAL (
                      SELECT proposed_by_user_id,approved_by_user_id,resource_teacher_user_id
                        FROM timetable_daily_cover_proposals
                       WHERE organization_id=c.organization_id AND case_id=c.id
                       ORDER BY CASE status WHEN 'APPROVED' THEN 0 WHEN 'AWAITING_APPROVAL' THEN 1 ELSE 2 END,id DESC
                       LIMIT 1
                  ) p ON TRUE
                 WHERE c.organization_id=:org AND c.id=:case
                """).param("org", organizationId).param("case", caseId)
                .query((rs, n) -> new CoverContext(rs.getLong(1), rs.getString(2),
                        rs.getObject(3, java.time.LocalDate.class), rs.getObject(4, java.time.LocalTime.class),
                        (Long) rs.getObject(5), (Long) rs.getObject(6), (Long) rs.getObject(7),
                        (Long) rs.getObject(8))).optional()
                .orElseThrow(() -> new NotificationDeliveryException("Coverage case no longer exists", null));
    }

    public List<DeadLetter> deadLetters(long organizationId) {
        return jdbc.sql("""
                SELECT source,id,event_id,event_type,attempt_count,last_error,dead_lettered_at FROM (
                    SELECT 'ATTENDANCE' source,id,event_id,event_type,attempt_count,last_error,dead_lettered_at,organization_id
                      FROM student_attendance_notification_outbox WHERE publication_status='DEAD_LETTER'
                    UNION ALL
                    SELECT 'DAILY_COVER',id,event_id,event_type,attempt_count,last_error,dead_lettered_at,organization_id
                      FROM timetable_daily_cover_notification_outbox WHERE publication_status='DEAD_LETTER'
                    UNION ALL
                    SELECT 'PUBLICATION',id,event_id,event_type,attempt_count,last_error,dead_lettered_at,organization_id
                      FROM academic_publication_notification_outbox WHERE publication_status='DEAD_LETTER'
                ) failures WHERE organization_id=:org ORDER BY dead_lettered_at DESC,id DESC
                """).param("org", organizationId).query((rs, n) -> new DeadLetter(
                        Source.valueOf(rs.getString(1)), rs.getLong(2), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getInt(5), rs.getString(6),
                        rs.getObject(7, OffsetDateTime.class))).list();
    }

    @Transactional
    public boolean requeue(long organizationId, long actorUserId, Source source, long id, OffsetDateTime now) {
        boolean changed = jdbc.sql("UPDATE " + table(source) + " SET publication_status='PENDING',attempt_count=0,"
                        + "available_at=:now,dead_lettered_at=NULL,last_error=NULL,lease_owner=NULL,lease_until=NULL,"
                        + "updated_at=:now WHERE id=:id AND organization_id=:org AND publication_status='DEAD_LETTER'")
                .param("now", now).param("id", id).param("org", organizationId).update() == 1;
        if (changed) {
            jdbc.sql("""
                    INSERT INTO academic_notification_delivery_audit
                        (organization_id,source,outbox_id,action,actor_user_id,occurred_at)
                    VALUES(:org,:source,:id,'DEAD_LETTER_REQUEUED',:actor,:now)
                    """).param("org", organizationId).param("source", source.name()).param("id", id)
                    .param("actor", actorUserId).param("now", now).update();
        }
        return changed;
    }

    private Item item(Source source, java.sql.ResultSet rs) throws SQLException {
        return new Item(source, rs.getLong("id"), rs.getObject("event_id", UUID.class),
                rs.getLong("organization_id"), (Long) rs.getObject("academic_year_id"),
                (Long) rs.getObject("student_user_id"), (Long) rs.getObject("case_id"),
                (Long) rs.getObject("artifact_id"), rs.getString("event_type"), rs.getString("payload"),
                rs.getObject("occurred_at", OffsetDateTime.class), rs.getInt("attempt_count"),
                array(rs.getArray("recipient_user_ids")));
    }

    private Set<Long> array(Array sqlArray) throws SQLException {
        if (sqlArray == null) return Set.of();
        Object[] values = (Object[]) sqlArray.getArray();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        Arrays.stream(values).forEach(value -> result.add(((Number) value).longValue()));
        return Set.copyOf(result);
    }

    private String table(Source source) {
        return switch (source) {
            case ATTENDANCE -> "student_attendance_notification_outbox";
            case DAILY_COVER -> "timetable_daily_cover_notification_outbox";
            case PUBLICATION -> "academic_publication_notification_outbox";
        };
    }

    private String safeError(Throwable failure) {
        String message = failure == null ? "Notification delivery failed" : failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
