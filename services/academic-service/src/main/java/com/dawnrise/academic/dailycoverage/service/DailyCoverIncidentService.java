package com.dawnrise.academic.dailycoverage.service;

import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "dawnrise.notification-delivery.enabled", havingValue = "true", matchIfMissing = true)
public class DailyCoverIncidentService {
    private static final Logger log = LoggerFactory.getLogger(DailyCoverIncidentService.class);
    private static final Duration INITIAL_WINDOW = Duration.ofMinutes(30);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private final JdbcClient jdbc;
    private final SchoolTimeZoneClient timeZones;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public record Incident(long id, long caseId, String status, int escalationLevel,
                           OffsetDateTime firstAlertAt, OffsetDateTime nextEscalationAt,
                           OffsetDateTime acknowledgedAt, Long acknowledgedByUserId,
                           OffsetDateTime resolvedAt) {
    }

    @Autowired
    public DailyCoverIncidentService(JdbcClient jdbc, SchoolTimeZoneClient timeZones,
                                     PlatformTransactionManager transactionManager) {
        this(jdbc, timeZones, Clock.systemUTC(), transactionManager);
    }

    DailyCoverIncidentService(JdbcClient jdbc, SchoolTimeZoneClient timeZones, Clock clock,
                              PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.timeZones = timeZones;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${dawnrise.notification-delivery.incident-delay:PT1M}",
            initialDelayString = "${dawnrise.notification-delivery.initial-delay:PT15S}")
    public void escalate() {
        List<CaseTime> cases = jdbc.sql("""
                SELECT id,organization_id,cover_date,start_time
                  FROM timetable_daily_cover_cases
                 WHERE status IN ('COVER_NEEDED','AWAITING_APPROVAL')
                   AND cover_date BETWEEN CURRENT_DATE-1 AND CURRENT_DATE+1
                 ORDER BY organization_id,cover_date,start_time,id
                """).query((rs, n) -> new CaseTime(rs.getLong(1), rs.getLong(2),
                        rs.getObject(3, java.time.LocalDate.class), rs.getObject(4, java.time.LocalTime.class))).list();
        for (CaseTime item : cases) {
            try {
                transactions.executeWithoutResult(status -> evaluate(item));
            } catch (RuntimeException exception) {
                log.warn("Daily cover incident evaluation failed for organization {} case {}",
                        item.organizationId(), item.caseId(), exception);
            }
        }
    }

    void evaluate(CaseTime item) {
        ZoneId zone = timeZones.getTimeZone(item.organizationId());
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        ZonedDateTime classStart = ZonedDateTime.of(item.date(), item.startTime(), zone);
        Duration untilStart = Duration.between(now, classStart);
        if (untilStart.compareTo(INITIAL_WINDOW) > 0) return;

        Incident incident = ensureIncident(item, now.toOffsetDateTime(), classStart.toOffsetDateTime());
        if (incident.acknowledgedAt() != null) return;
        if (!classStart.isAfter(now)) {
            emit(item.organizationId(), item.caseId(), "CLASS_UNCOVERED",
                    "class-start-uncovered:" + item.caseId(), incident.id());
            updateEscalation(incident.id(), item.organizationId(), 2, null);
            return;
        }
        emit(item.organizationId(), item.caseId(), "IMMINENT_UNCOVERED",
                "imminent-uncovered:" + item.caseId(), incident.id());
        if (!incident.firstAlertAt().plus(TEN_MINUTES).isAfter(now.toOffsetDateTime())
                && incident.escalationLevel() < 1) {
            emit(item.organizationId(), item.caseId(), "IMMINENT_UNCOVERED",
                    "unacknowledged-ten-minute:" + item.caseId(), incident.id());
            updateEscalation(incident.id(), item.organizationId(), 1, classStart.toOffsetDateTime());
        }
    }

    @Transactional(readOnly = true)
    public List<Incident> open(long organizationId) {
        return jdbc.sql("""
                SELECT id,case_id,status,escalation_level,first_alert_at,next_escalation_at,
                       acknowledged_at,acknowledged_by_user_id,resolved_at
                  FROM timetable_daily_cover_incidents
                 WHERE organization_id=:org AND status='OPEN' ORDER BY first_alert_at,id
                """).param("org", organizationId).query((rs, n) -> map(rs)).list();
    }

    @Transactional
    public boolean acknowledge(long organizationId, long incidentId, long actorUserId) {
        return jdbc.sql("""
                UPDATE timetable_daily_cover_incidents
                   SET acknowledged_at=COALESCE(acknowledged_at,CURRENT_TIMESTAMP),
                       acknowledged_by_user_id=COALESCE(acknowledged_by_user_id,:actor),
                       next_escalation_at=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP
                 WHERE id=:id AND organization_id=:org AND status='OPEN'
                """).param("actor", actorUserId).param("id", incidentId).param("org", organizationId)
                .update() == 1;
    }

    private Incident ensureIncident(CaseTime item, OffsetDateTime now, OffsetDateTime classStart) {
        return jdbc.sql("""
                INSERT INTO timetable_daily_cover_incidents
                    (organization_id,case_id,status,first_alert_at,next_escalation_at,escalation_level)
                VALUES(:org,:case,'OPEN',:now,LEAST(:tenMinute,:classStart),0)
                ON CONFLICT(organization_id,case_id) DO UPDATE SET
                    status='OPEN',resolved_at=NULL,
                    first_alert_at=CASE WHEN timetable_daily_cover_incidents.status='RESOLVED'
                                        THEN EXCLUDED.first_alert_at
                                        ELSE timetable_daily_cover_incidents.first_alert_at END,
                    next_escalation_at=CASE WHEN timetable_daily_cover_incidents.status='RESOLVED'
                                            THEN EXCLUDED.next_escalation_at
                                            ELSE timetable_daily_cover_incidents.next_escalation_at END,
                    escalation_level=CASE WHEN timetable_daily_cover_incidents.status='RESOLVED'
                                          THEN 0 ELSE timetable_daily_cover_incidents.escalation_level END,
                    acknowledged_at=CASE WHEN timetable_daily_cover_incidents.status='RESOLVED'
                                         THEN NULL ELSE timetable_daily_cover_incidents.acknowledged_at END,
                    acknowledged_by_user_id=CASE WHEN timetable_daily_cover_incidents.status='RESOLVED'
                                                  THEN NULL ELSE timetable_daily_cover_incidents.acknowledged_by_user_id END,
                    version=timetable_daily_cover_incidents.version+1,updated_at=CURRENT_TIMESTAMP
                RETURNING id,case_id,status,escalation_level,first_alert_at,next_escalation_at,
                          acknowledged_at,acknowledged_by_user_id,resolved_at
                """).param("org", item.organizationId()).param("case", item.caseId()).param("now", now)
                .param("tenMinute", now.plus(TEN_MINUTES)).param("classStart", classStart)
                .query((rs, n) -> map(rs)).single();
    }

    private void emit(long organizationId, long caseId, String type, String key, long incidentId) {
        jdbc.sql("""
                INSERT INTO timetable_daily_cover_notification_outbox
                    (event_id,organization_id,case_id,event_type,idempotency_key,payload)
                VALUES(:event,:org,:case,:type,:key,CAST(:payload AS JSONB))
                ON CONFLICT(organization_id,idempotency_key) DO NOTHING
                """).param("event", UUID.randomUUID()).param("org", organizationId).param("case", caseId)
                .param("type", type).param("key", key)
                .param("payload", "{\"caseId\":" + caseId + ",\"incidentId\":" + incidentId + "}")
                .update();
    }

    private void updateEscalation(long incidentId, long organizationId, int level, OffsetDateTime next) {
        jdbc.sql("""
                UPDATE timetable_daily_cover_incidents
                   SET escalation_level=GREATEST(escalation_level,:level),next_escalation_at=:next,
                       version=version+1,updated_at=CURRENT_TIMESTAMP
                 WHERE id=:id AND organization_id=:org AND status='OPEN' AND acknowledged_at IS NULL
                """).param("level", level).param("next", next).param("id", incidentId)
                .param("org", organizationId).update();
    }

    private Incident map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Incident(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4),
                rs.getObject(5, OffsetDateTime.class), rs.getObject(6, OffsetDateTime.class),
                rs.getObject(7, OffsetDateTime.class), (Long) rs.getObject(8),
                rs.getObject(9, OffsetDateTime.class));
    }

    record CaseTime(long caseId, long organizationId, java.time.LocalDate date,
                    java.time.LocalTime startTime) {
    }
}
