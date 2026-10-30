package com.dawnrise.identity.notification.service;

import com.dawnrise.identity.notification.dto.IngestNotificationRequest;
import com.dawnrise.identity.notification.dto.NotificationAction;
import com.dawnrise.identity.notification.exception.NotificationNotFoundException;
import com.dawnrise.identity.notification.model.NotificationActionType;
import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationEventType;
import com.dawnrise.identity.notification.model.NotificationFilter;
import com.dawnrise.identity.notification.model.NotificationImportance;
import com.dawnrise.identity.notification.email.NotificationEmailOutboxStore;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.repository.UserRepository;
import com.dawnrise.identity.studentguardian.service.ActiveGuardianRecipientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {
    private final MutableClock clock = new MutableClock(ZoneId.of("UTC"));
    private NotificationServiceImpl service;
    private UserRepository users;
    private TransactionTemplate transaction;
    private NamedParameterJdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        String databaseUrl = System.getenv("NOTIFICATION_TEST_DATABASE_URL");
        Assumptions.assumeTrue(isExplicitDisposableDatabase(databaseUrl),
                "Notification PostgreSQL tests require a loopback database named dawnrise_notification_test_*");
        dataSource = new DriverManagerDataSource(
                databaseUrl,
                System.getenv().getOrDefault("NOTIFICATION_TEST_DATABASE_USER", "postgres"),
                System.getenv().getOrDefault("NOTIFICATION_TEST_DATABASE_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().execute(
                "TRUNCATE student_guardian_relationships, notification_email_delivery_audit, "
                        + "notification_email_outbox, notification_recipients, notification_events, "
                        + "notification_preferences CASCADE");
        jdbc.getJdbcTemplate().update("DELETE FROM users WHERE organization_id IN (1,2)");
        jdbc.getJdbcTemplate().update("DELETE FROM organizations WHERE id IN (1,2)");
        jdbc.getJdbcTemplate().update(
                "INSERT INTO organizations(id,school_code,name,status) VALUES(1,'NTEST','Notification Test','ACTIVE')");
        jdbc.getJdbcTemplate().update("""
                INSERT INTO users(id,organization_id,username,first_name,email,status)
                VALUES(10,1,'notify-10','Notify','notify10@example.test','ACTIVE'),
                      (11,1,'notify-11','Notify','notify11@example.test','ACTIVE')
                """);
        users = mock(UserRepository.class);
        service = new NotificationServiceImpl(jdbc, users, clock);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void ingestionIsIdempotentAndDeduplicatesRecipients() {
        allowUsers(1L, 10L, 11L);
        var first = service.ingest("academic-service", request(1L, "source-1", Set.of(10L, 11L)));
        var duplicate = service.ingest("academic-service", request(1L, "source-1", Set.of(10L, 11L)));

        assertTrue(first.created());
        assertEquals(2, first.recipientCount());
        assertFalse(duplicate.created());
        assertEquals(0, duplicate.recipientCount());
        assertEquals(first.eventId(), duplicate.eventId());
        assertEquals(1, service.unreadCount(1L, 10L));
        assertEquals(1, service.unreadCount(1L, 11L));
    }

    @Test
    void concurrentIngestionCreatesOneEventAndOneRecipientSet() throws Exception {
        allowUsers(1L, 10L, 11L);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                start.await();
                return transaction.execute(status -> service.ingest(
                        "academic-service", request(1L, "source-concurrent", Set.of(10L, 11L))));
            });
            var second = pool.submit(() -> {
                start.await();
                return transaction.execute(status -> service.ingest(
                        "academic-service", request(1L, "source-concurrent", Set.of(10L, 11L))));
            });
            start.countDown();
            var firstResult = first.get();
            var secondResult = second.get();

            assertEquals(1, (firstResult.created() ? 1 : 0) + (secondResult.created() ? 1 : 0));
            assertEquals(2, firstResult.recipientCount() + secondResult.recipientCount());
            assertEquals(firstResult.eventId(), secondResult.eventId());
            assertEquals(1, service.unreadCount(1L, 10L));
            assertEquals(1, service.unreadCount(1L, 11L));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void everyReadAndMutationIsTenantAndRecipientScoped() {
        allowUsers(1L, 10L, 11L);
        service.ingest("academic-service", request(1L, "source-2", Set.of(10L, 11L)));
        var item = service.navbarPreview(1L, 10L).getFirst();

        assertThrows(NotificationNotFoundException.class,
                () -> service.detail(2L, 10L, item.id()));
        assertThrows(NotificationNotFoundException.class,
                () -> service.detail(1L, 11L, item.id()));
        assertThrows(NotificationNotFoundException.class,
                () -> service.delete(2L, 10L, item.id()));
    }

    @Test
    void hidingOnlyRemovesNavbarPreviewAndPreservesUnreadInboxItem() {
        allowUsers(1L, 10L);
        service.ingest("academic-service", request(1L, "source-3", Set.of(10L)));
        var item = service.navbarPreview(1L, 10L).getFirst();

        service.hideFromNavbar(1L, 10L, item.id());

        assertTrue(service.navbarPreview(1L, 10L).isEmpty());
        assertEquals(1, service.unreadCount(1L, 10L));
        assertEquals(1, service.inbox(1L, 10L, NotificationFilter.ALL,
                null, 0, 20).content().size());
    }

    @Test
    void deleteIsRecipientLocalAndUndoRestoresWithinWindowButRejectsExpiry() {
        allowUsers(1L, 10L, 11L);
        service.ingest("academic-service", request(1L, "source-4", Set.of(10L, 11L)));
        var userTenItem = service.navbarPreview(1L, 10L).getFirst();

        service.delete(1L, 10L, userTenItem.id());
        assertEquals(0, service.unreadCount(1L, 10L));
        assertEquals(1, service.unreadCount(1L, 11L));

        service.undoDelete(1L, 10L, userTenItem.id());
        assertEquals(1, service.unreadCount(1L, 10L));

        service.delete(1L, 10L, userTenItem.id());
        clock.advance(Duration.ofSeconds(31));

        assertThrows(NotificationNotFoundException.class,
                () -> service.undoDelete(1L, 10L, userTenItem.id()));
        assertEquals(0, service.unreadCount(1L, 10L));
        assertEquals(1, service.unreadCount(1L, 11L));
    }

    @Test
    void readAllUsesACutoffSoLaterDeliveryRemainsUnread() {
        allowUsers(1L, 10L);
        service.ingest("academic-service", request(1L, "source-5", Set.of(10L)));
        assertEquals(0, service.markAllRead(1L, 10L));

        service.ingest("academic-service", request(1L, "source-6", Set.of(10L)));

        assertEquals(1, service.unreadCount(1L, 10L));
    }

    @Test
    void concurrentReadAllAndDeliveryRemainConsistent() throws Exception {
        allowUsers(1L, 10L);
        service.ingest("academic-service", request(1L, "source-before-read-all", Set.of(10L)));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var readAll = pool.submit(() -> {
                start.await();
                return transaction.execute(status -> service.markAllRead(1L, 10L));
            });
            var delivery = pool.submit(() -> {
                start.await();
                return transaction.execute(status -> service.ingest(
                        "academic-service", request(1L, "source-during-read-all", Set.of(10L))));
            });
            start.countDown();
            readAll.get();
            delivery.get();

            assertEquals(2, service.inbox(1L, 10L, NotificationFilter.ALL,
                    null, 0, 20).totalElements());
            assertTrue(service.unreadCount(1L, 10L) <= 1,
                    "Only delivery ordered after the read-all cutoff may remain unread");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void resolvingCoverageGroupClearsPendingYellowButKeepsConfirmedCurrent() {
        allowUsers(1L, 10L);
        service.ingest("academic-service", request(1L, "pending-source", Set.of(10L)));
        service.ingest("academic-service", request(1L, "confirmed-source", Set.of(10L)));

        var result = service.resolveGroup("academic-service", 1L, "cover:20", "confirmed-source");

        assertEquals(1, result.resolvedEvents());
        assertEquals(1, service.unreadCount(1L, 10L));
        assertEquals(1, service.navbarPreview(1L, 10L).size());
        assertEquals(2, service.inbox(1L, 10L, NotificationFilter.ALL, null, 0, 20).totalElements());
        assertEquals("CANCELLED", jdbc.getJdbcTemplate().queryForObject("""
                SELECT status FROM notification_email_outbox outbox
                JOIN notification_events event ON event.id=outbox.event_id
                WHERE event.source_event_id='pending-source'
                """, String.class));
    }

    @Test
    void optionalCategoryCanSuppressInboxAndEmail() {
        preferences(false, "DISABLED", false, null, null);
        allowUsers(1L, 10L);

        service.ingest("academic-service", request(1L, "suppressed", Set.of(10L)));

        assertEquals(0, service.unreadCount(1L, 10L));
        assertEquals(0, jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM notification_email_outbox", Integer.class));
    }

    @Test
    void urgentDeliveryBypassesDisabledPreferenceAndQuietHours() {
        preferences(false, "DISABLED", true, java.time.LocalTime.of(0, 1), java.time.LocalTime.of(23, 59));
        allowUsers(1L, 10L);
        var urgent = new IngestNotificationRequest(1L, "urgent-uncovered",
                NotificationEventType.CLASS_UNCOVERED, NotificationCategory.TIMETABLE,
                NotificationImportance.URGENT, "Class cover is urgently needed", "Open Dawnrise for details",
                new NotificationAction(NotificationActionType.DAILY_COVERAGE, 20L, null, 30L),
                "cover:20", OffsetDateTime.now(clock), Set.of(10L));

        service.ingest("academic-service", urgent);

        assertEquals(1, service.unreadCount(1L, 10L));
        assertEquals("IMMEDIATE", jdbc.getJdbcTemplate().queryForObject(
                "SELECT delivery_mode FROM notification_email_outbox", String.class));
        assertTrue(jdbc.getJdbcTemplate().queryForObject(
                "SELECT available_at<=CURRENT_TIMESTAMP FROM notification_email_outbox", Boolean.class));
    }

    @Test
    void digestDeliveryIsDeferredAndDoesNotDuplicateForRepeatedSourceEvent() {
        preferences(true, "DIGEST", false, null, null);
        allowUsers(1L, 10L);
        var event = request(1L, "digest-event", Set.of(10L));

        service.ingest("academic-service", event);
        service.ingest("academic-service", event);

        assertEquals(1, jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM notification_email_outbox", Integer.class));
        assertEquals("DIGEST", jdbc.getJdbcTemplate().queryForObject(
                "SELECT delivery_mode FROM notification_email_outbox", String.class));
        assertTrue(jdbc.getJdbcTemplate().queryForObject(
                "SELECT available_at>CURRENT_TIMESTAMP FROM notification_email_outbox", Boolean.class));
    }

    @Test
    void emailLeaseExpiresAndDeadLetterRequeueIsAudited() {
        allowUsers(1L, 10L);
        service.ingest("academic-service", request(1L, "lease-event", Set.of(10L)));
        var store = new NotificationEmailOutboxStore(new NamedParameterJdbcTemplate(dataSource));
        OffsetDateTime now = OffsetDateTime.now();
        UUID firstOwner = UUID.randomUUID();
        var first = store.claim(firstOwner, now, Duration.ofSeconds(10), 10);
        assertEquals(1, first.size());
        assertTrue(store.claim(UUID.randomUUID(), now.plusSeconds(5), Duration.ofSeconds(10), 10).isEmpty());
        UUID recoveryOwner = UUID.randomUUID();
        var recovered = store.claim(recoveryOwner, now.plusSeconds(11), Duration.ofSeconds(10), 10);
        assertEquals(first.getFirst().id(), recovered.getFirst().id());
        jdbc.getJdbcTemplate().update("UPDATE notification_email_outbox SET attempt_count=5 WHERE id=?",
                recovered.getFirst().id());
        var exhausted = new NotificationEmailOutboxStore.Item(recovered.getFirst().id(), 1L,
                recovered.getFirst().eventId(), 10L, recovered.getFirst().email(), recovered.getFirst().templateKey(),
                recovered.getFirst().subject(), recovered.getFirst().variables(), recovered.getFirst().actionType(),
                recovered.getFirst().actionReferenceId(), recovered.getFirst().mode(), recovered.getFirst().messageId(), 5);
        store.failed(List.of(exhausted), recoveryOwner, now.plusSeconds(12), 5, Duration.ofMinutes(1),
                new IllegalStateException("safe failure"));
        assertEquals(1, store.deadLetters(1L).size());
        assertTrue(store.requeue(1L, 10L, exhausted.id(), now.plusSeconds(13)));
        assertEquals(1, jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM notification_email_delivery_audit", Integer.class));
    }

    @Test
    void activeGuardianRecipientsExcludeInactiveRelationshipsAccountsAndOtherTenants() {
        jdbc.getJdbcTemplate().update(
                "INSERT INTO organizations(id,school_code,name,status) VALUES(2,'NTEST2','Other Test','ACTIVE')");
        jdbc.getJdbcTemplate().update("""
                INSERT INTO users(id,organization_id,username,first_name,status)
                VALUES(12,1,'notify-12','Inactive','INACTIVE'),
                      (13,1,'notify-13','Ended','ACTIVE'),
                      (20,2,'notify-20','Other Student','ACTIVE'),
                      (21,2,'notify-21','Other Guardian','ACTIVE')
                """);
        jdbc.getJdbcTemplate().update("""
                INSERT INTO student_guardian_relationships
                    (organization_id,student_user_id,guardian_user_id,relationship_type,status,
                     primary_guardian,created_by_user_id,started_at,ended_at,ended_by_user_id)
                VALUES(1,10,11,'GUARDIAN','ACTIVE',TRUE,10,CURRENT_TIMESTAMP,NULL,NULL),
                      (1,10,12,'GUARDIAN','ACTIVE',FALSE,10,CURRENT_TIMESTAMP,NULL,NULL),
                      (1,10,13,'GUARDIAN','ENDED',FALSE,10,CURRENT_TIMESTAMP,
                       CURRENT_TIMESTAMP,10),
                      (2,20,21,'GUARDIAN','ACTIVE',TRUE,20,CURRENT_TIMESTAMP,NULL,NULL)
                """);

        var recipients = new ActiveGuardianRecipientService(jdbc).activeGuardianUserIds(1, 10);

        assertEquals(List.of(11L), recipients);
    }

    private IngestNotificationRequest request(long organizationId, String sourceEventId,
                                               Set<Long> recipients) {
        return new IngestNotificationRequest(organizationId, sourceEventId,
                NotificationEventType.COVER_NEEDED, NotificationCategory.TIMETABLE,
                NotificationImportance.IMPORTANT, "Cover needed", "Grade 1, Section A",
                new NotificationAction(NotificationActionType.DAILY_COVERAGE, 20L, null, 30L),
                "cover:20", OffsetDateTime.now(clock), recipients);
    }

    private void allowUsers(long organizationId, Long... ids) {
        List<User> matching = java.util.Arrays.stream(ids).map(id -> mock(User.class)).toList();
        when(users.findAllByOrganizationIdAndIdIn(eq(organizationId), anyCollection()))
                .thenReturn(matching);
    }

    private void preferences(boolean timetableEnabled, String emailMode, boolean quiet,
                             java.time.LocalTime start, java.time.LocalTime end) {
        jdbc.getJdbcTemplate().update("""
                INSERT INTO notification_preferences(
                    organization_id,user_id,timetable_enabled,timetable_email_mode,
                    quiet_hours_enabled,quiet_hours_start,quiet_hours_end,time_zone)
                VALUES(1,10,?,?,?,?,?,'UTC')
                """, timetableEnabled, emailMode, quiet, start, end);
    }

    private boolean isExplicitDisposableDatabase(String databaseUrl) {
        if (databaseUrl == null || !databaseUrl.startsWith("jdbc:postgresql://")) return false;
        try {
            URI uri = URI.create(databaseUrl.substring("jdbc:".length()));
            boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            return loopback && uri.getPath() != null
                    && uri.getPath().matches("/dawnrise_notification_test_[a-zA-Z0-9_]+");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static final class MutableClock extends Clock {
        private final ZoneId zone;
        private Duration offset = Duration.ZERO;

        private MutableClock(ZoneId zone) {
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(requestedZone);
        }

        @Override
        public java.time.Instant instant() {
            return java.time.Instant.now().plus(offset);
        }

        private void advance(Duration duration) {
            offset = offset.plus(duration);
        }
    }
}
