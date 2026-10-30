package com.dawnrise.academic.notificationdelivery;

import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.Source;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AcademicNotificationDeliveryPostgresTest {
    private JdbcClient jdbc;
    private AcademicNotificationOutboxStore store;
    private TransactionTemplate transactions;

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
        store = new AcademicNotificationOutboxStore(jdbc);
        transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        jdbc.sql("TRUNCATE academic_publication_notification_outbox").update();
    }

    @Test
    void migrationCreatesReliableDeliveryTablesAndColumns() {
        assertEquals(3, jdbc.sql("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema='public' AND table_name IN
                    ('academic_publication_notification_outbox','timetable_daily_cover_incidents',
                     'timetable_daily_cover_notification_outbox')
                """).query(Integer.class).single());
        assertEquals("35", jdbc.sql("SELECT version FROM flyway_schema_history WHERE version='35' AND success")
                .query(String.class).single());
    }

    @Test
    void concurrentWorkersClaimEachRowOnlyOnce() throws Exception {
        insert("concurrent-1");
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return transactions.execute(status ->
                    store.claim(Source.PUBLICATION, UUID.randomUUID(), OffsetDateTime.now(),
                            Duration.ofMinutes(1), 10)); });
            var second = pool.submit(() -> { start.await(); return transactions.execute(status ->
                    store.claim(Source.PUBLICATION, UUID.randomUUID(), OffsetDateTime.now(),
                            Duration.ofMinutes(1), 10)); });
            start.countDown();
            assertEquals(1, first.get().size() + second.get().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredLeaseIsRecoveredAfterWorkerRestart() {
        insert("expired-lease");
        jdbc.sql("UPDATE academic_publication_notification_outbox SET lease_owner=:owner,"
                        + "lease_until=CURRENT_TIMESTAMP-INTERVAL '1 minute'")
                .param("owner", UUID.randomUUID()).update();
        assertEquals(1, transactions.execute(status -> store.claim(Source.PUBLICATION, UUID.randomUUID(),
                OffsetDateTime.now(), Duration.ofMinutes(1), 10)).size());
    }

    @Test
    void deadLetterCanBeInspectedAndRequeued() {
        insert("dead-letter");
        UUID owner = UUID.randomUUID();
        var item = transactions.execute(status -> store.claim(Source.PUBLICATION, owner,
                OffsetDateTime.now(), Duration.ofMinutes(1), 10)).getFirst();
        store.recordFailure(item, owner, OffsetDateTime.now(), 1, Duration.ofSeconds(1),
                new IllegalStateException("identity unavailable"));
        assertEquals(1, store.deadLetters(1).size());
        assertTrue(store.requeue(1, 900, Source.PUBLICATION, item.id(), OffsetDateTime.now()));
        assertEquals(0, store.deadLetters(1).size());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM academic_notification_delivery_audit "
                        + "WHERE organization_id=1 AND source='PUBLICATION' AND outbox_id=:id "
                        + "AND action='DEAD_LETTER_REQUEUED' AND actor_user_id=900")
                .param("id", item.id()).query(Integer.class).single());
    }

    @Test
    void sourceTransactionRollbackRemovesPublicationEvent() {
        transactions.executeWithoutResult(status -> {
            insert("rollback");
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM academic_publication_notification_outbox")
                .query(Integer.class).single());
    }

    @Test
    void duplicatePublicationEventIsIdempotent() {
        insert("duplicate");
        insert("duplicate");
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM academic_publication_notification_outbox")
                .query(Integer.class).single());
    }

    private void insert(String key) {
        jdbc.sql("""
                INSERT INTO academic_publication_notification_outbox
                    (event_id,organization_id,academic_year_id,student_user_id,artifact_id,event_type,
                     idempotency_key,payload,occurred_at)
                VALUES(:event,1,2,21,40,'RESULT_PUBLISHED',:key,'{}',CURRENT_TIMESTAMP)
                ON CONFLICT(organization_id,idempotency_key) DO NOTHING
                """).param("event", UUID.randomUUID()).param("key", key).update();
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
}
