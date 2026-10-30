package com.dawnrise.academic.dailycoverage.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DailyCoverIncidentPostgresTest {
    private JdbcClient jdbc;
    private TransactionTemplate transactions;
    private MutableClock clock;
    private DailyCoverIncidentService incidents;

    @BeforeEach
    void setUp() {
        String url = System.getenv("ACADEMIC_NOTIFICATION_TEST_DATABASE_URL");
        assumeTrue(disposable(url), "Requires loopback database dawnrise_academic_notification_test_*");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("ACADEMIC_NOTIFICATION_TEST_DATABASE_USER", "postgres"),
                System.getenv().getOrDefault("ACADEMIC_NOTIFICATION_TEST_DATABASE_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("filesystem:src/main/resources/db/migration")
                .load().migrate();
        jdbc = JdbcClient.create(dataSource);
        var transactionManager = new JdbcTransactionManager(dataSource);
        transactions = new TransactionTemplate(transactionManager);
        clock = new MutableClock(Instant.now().atZone(ZoneId.of("UTC")).withSecond(0).withNano(0).toInstant());
        incidents = new DailyCoverIncidentService(jdbc, organizationId -> ZoneId.of("UTC"), clock,
                transactionManager);
        jdbc.sql("DROP TRIGGER IF EXISTS fail_daily_cover_outbox ON timetable_daily_cover_notification_outbox").update();
        jdbc.sql("DROP FUNCTION IF EXISTS fail_daily_cover_outbox()").update();
        jdbc.sql("TRUNCATE timetable_daily_cover_cases CASCADE").update();
    }

    @AfterEach
    void removeFailureTrigger() {
        if (jdbc != null) {
            jdbc.sql("DROP TRIGGER IF EXISTS fail_daily_cover_outbox ON timetable_daily_cover_notification_outbox").update();
            jdbc.sql("DROP FUNCTION IF EXISTS fail_daily_cover_outbox()").update();
        }
    }

    @Test
    void incidentPersistsTenMinuteAndClassStartEscalationsAndCanBeResolved() {
        long caseId = insertCase(LocalTime.now(clock).plusMinutes(20));

        incidents.escalate();
        var initial = incidents.open(1).getFirst();
        assertEquals(0, initial.escalationLevel());
        assertEquals(1, outboxCount(caseId));

        clock.advance(Duration.ofMinutes(11));
        incidents.escalate();
        assertEquals(1, incidents.open(1).getFirst().escalationLevel());
        assertEquals(2, outboxCount(caseId));

        clock.advance(Duration.ofMinutes(10));
        incidents.escalate();
        assertEquals(2, incidents.open(1).getFirst().escalationLevel());
        assertEquals(3, outboxCount(caseId));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM timetable_daily_cover_notification_outbox "
                        + "WHERE case_id=:case AND event_type='CLASS_UNCOVERED'")
                .param("case", caseId).query(Integer.class).single());

        jdbc.sql("UPDATE timetable_daily_cover_incidents SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP," +
                        "next_escalation_at=NULL WHERE organization_id=1 AND case_id=:case")
                .param("case", caseId).update();
        assertTrue(incidents.open(1).isEmpty());
    }

    @Test
    void acknowledgementStopsFurtherEscalation() {
        long caseId = insertCase(LocalTime.now(clock).plusMinutes(20));
        incidents.escalate();
        var incident = incidents.open(1).getFirst();

        assertTrue(incidents.acknowledge(1, incident.id(), 700));
        clock.advance(Duration.ofMinutes(25));
        incidents.escalate();

        var acknowledged = incidents.open(1).getFirst();
        assertNotNull(acknowledged.acknowledgedAt());
        assertEquals(700L, acknowledged.acknowledgedByUserId());
        assertNull(acknowledged.nextEscalationAt());
        assertEquals(1, outboxCount(caseId));
    }

    @Test
    void outboxFailureRollsBackIncidentCreationAndEscalationState() {
        long caseId = insertCase(LocalTime.now(clock).plusMinutes(20));
        jdbc.sql("""
                CREATE FUNCTION fail_daily_cover_outbox() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'forced outbox failure'; END $$
                """).update();
        jdbc.sql("""
                CREATE TRIGGER fail_daily_cover_outbox BEFORE INSERT
                ON timetable_daily_cover_notification_outbox FOR EACH ROW
                EXECUTE FUNCTION fail_daily_cover_outbox()
                """).update();

        incidents.escalate();

        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM timetable_daily_cover_incidents WHERE case_id=:case")
                .param("case", caseId).query(Integer.class).single());
        assertEquals(0, outboxCount(caseId));
    }

    private long insertCase(LocalTime startTime) {
        return transactions.execute(status -> {
            jdbc.sql("SET LOCAL session_replication_role='replica'").update();
            return jdbc.sql("""
                    INSERT INTO timetable_daily_cover_cases
                        (organization_id,academic_year_id,cover_date,publication_id,publication_entry_id,
                         absence_id,section_id,section_name,wing_id,wing_name,start_time,end_time,
                         interval_type,original_activity_name,original_teacher_user_id,
                         original_teacher_display_name,status)
                    VALUES(1,1,:date,1,1,1,1,'Section A',1,'Junior',:start,:end,
                           'TEACHING','Mathematics',600,'Teacher','COVER_NEEDED')
                    RETURNING id
                    """).param("date", LocalDate.now(clock)).param("start", startTime)
                    .param("end", startTime.plusMinutes(40)).query(Long.class).single();
        });
    }

    private int outboxCount(long caseId) {
        return jdbc.sql("SELECT COUNT(*) FROM timetable_daily_cover_notification_outbox WHERE case_id=:case")
                .param("case", caseId).query(Integer.class).single();
    }

    private boolean disposable(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) return false;
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            return ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                    && uri.getPath() != null
                    && uri.getPath().matches("/dawnrise_academic_notification_test_[a-zA-Z0-9_]+");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
    }
}
