package com.dawnrise.academic.notificationdelivery;

import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "dawnrise.notification-delivery.enabled", havingValue = "true", matchIfMissing = true)
public class TimetableExpiryNotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(TimetableExpiryNotificationScheduler.class);
    private final JdbcClient jdbc;
    private final SchoolTimeZoneClient schoolTimeZones;
    private final Clock clock;

    @Autowired
    public TimetableExpiryNotificationScheduler(JdbcClient jdbc, SchoolTimeZoneClient schoolTimeZones) {
        this(jdbc, schoolTimeZones, Clock.systemUTC());
    }

    TimetableExpiryNotificationScheduler(JdbcClient jdbc, SchoolTimeZoneClient schoolTimeZones, Clock clock) {
        this.jdbc = jdbc;
        this.schoolTimeZones = schoolTimeZones;
        this.clock = clock;
    }

    @Scheduled(cron = "${dawnrise.notification-delivery.timetable-expiry-cron:0 15 * * * *}")
    @Transactional
    public void enqueueExpiringTimetables() {
        LocalDate utcToday = LocalDate.now(clock);
        List<Publication> candidates = jdbc.sql("""
                SELECT p.id,p.organization_id,p.academic_year_id,p.effective_to_exclusive
                  FROM timetable_publications p
                 WHERE p.effective_to_exclusive BETWEEN :from AND :to
                   AND NOT EXISTS (
                       SELECT 1 FROM timetable_publications successor
                        WHERE successor.organization_id=p.organization_id
                          AND successor.academic_year_id=p.academic_year_id
                          AND successor.effective_from=p.effective_to_exclusive)
                 ORDER BY p.organization_id,p.id
                """).param("from", utcToday.minusDays(1)).param("to", utcToday.plusDays(5))
                .query((rs, row) -> new Publication(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getObject(4, LocalDate.class))).list();
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);
        for (Publication publication : candidates) {
            LocalDate today;
            try {
                today = LocalDate.now(clock.withZone(
                        schoolTimeZones.getTimeZone(publication.organizationId())));
            } catch (RuntimeException exception) {
                log.warn("Could not evaluate timetable expiry for organization {} publication {}",
                        publication.organizationId(), publication.id(), exception);
                continue;
            }
            LocalDate finalDay = publication.effectiveToExclusive().minusDays(1);
            String eventType = eventType(today, publication.effectiveToExclusive());
            if ("TIMETABLE_EXPIRING".equals(eventType)) {
                enqueue(publication, eventType, "ADVANCE", finalDay, occurredAt);
            } else if ("TIMETABLE_EXPIRES_TODAY".equals(eventType)) {
                enqueue(publication, eventType, "FINAL", finalDay, occurredAt);
            }
        }
    }

    static String eventType(LocalDate today, LocalDate effectiveToExclusive) {
        long daysRemaining = ChronoUnit.DAYS.between(today, effectiveToExclusive.minusDays(1));
        if (daysRemaining >= 1 && daysRemaining <= 3) return "TIMETABLE_EXPIRING";
        if (daysRemaining == 0) return "TIMETABLE_EXPIRES_TODAY";
        return null;
    }

    private void enqueue(Publication publication, String eventType, String phase,
                         LocalDate finalDay, OffsetDateTime occurredAt) {
        String key = "timetable-expiry:" + publication.id() + ":" + phase;
        jdbc.sql("""
                INSERT INTO timetable_expiry_notification_outbox
                    (event_id,organization_id,academic_year_id,publication_id,event_type,
                     idempotency_key,payload,occurred_at)
                VALUES(:event,:org,:year,:publication,:type,:key,
                       JSONB_BUILD_OBJECT('finalEffectiveDate',CAST(:finalDay AS TEXT)),:occurredAt)
                ON CONFLICT(organization_id,idempotency_key) DO NOTHING
                """).param("event", UUID.randomUUID()).param("org", publication.organizationId())
                .param("year", publication.academicYearId()).param("publication", publication.id())
                .param("type", eventType).param("key", key).param("finalDay", finalDay)
                .param("occurredAt", occurredAt).update();
    }

    record Publication(long id, long organizationId, long academicYearId,
                       LocalDate effectiveToExclusive) {}
}
