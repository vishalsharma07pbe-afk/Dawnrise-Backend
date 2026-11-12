package com.dawnrise.identity.notification.email;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationEmailDeliveryWorkerPostgresTest {
    private NamedParameterJdbcTemplate jdbc;
    private JavaMailSender mailSender;
    private NotificationEmailDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        String databaseUrl = System.getenv("NOTIFICATION_TEST_DATABASE_URL");
        Assumptions.assumeTrue(isExplicitDisposableDatabase(databaseUrl),
                "Email worker test requires a loopback database named dawnrise_notification_test_*");
        var dataSource = new DriverManagerDataSource(
                databaseUrl,
                System.getenv().getOrDefault("NOTIFICATION_TEST_DATABASE_USER", "postgres"),
                System.getenv().getOrDefault("NOTIFICATION_TEST_DATABASE_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().execute(
                "TRUNCATE notification_email_delivery_audit, notification_email_outbox, "
                        + "notification_recipients, notification_events CASCADE");
        jdbc.getJdbcTemplate().update("DELETE FROM users WHERE organization_id=901");
        jdbc.getJdbcTemplate().update("DELETE FROM organizations WHERE id=901");
        jdbc.getJdbcTemplate().update(
                "INSERT INTO organizations(id,school_code,name,status) VALUES(901,'EMAILTEST','Email Test','ACTIVE')");
        jdbc.getJdbcTemplate().update("""
                INSERT INTO users(id,organization_id,username,password_hash,first_name,email,status)
                VALUES(901,901,'email-worker-test','test-only','Email','recipient@example.test','ACTIVE')
                """);
        mailSender = mock(JavaMailSender.class);
        worker = new NotificationEmailDeliveryWorker(
                new NotificationEmailOutboxStore(jdbc), mailSender, new ObjectMapper());
    }

    @Test
    void pendingEmailIsClaimedAndMarkedSentAfterSuccessfulSmtpHandoff() {
        UUID outboxId = insertPendingEmail();

        worker.deliver();

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getText())
                .contains("Email check", "Test delivery", "signing in");
        assertSent(outboxId);
    }

    @Test
    void pendingEmailIsAcceptedByIsolatedMailpit() {
        Assumptions.assumeTrue("true".equals(System.getenv("MAILPIT_TEST_ENABLED")),
                "Explicit Mailpit test confirmation is required");
        UUID outboxId = insertPendingEmail();
        JavaMailSenderImpl mailpit = new JavaMailSenderImpl();
        mailpit.setHost(System.getenv().getOrDefault("MAILPIT_TEST_HOST", "localhost"));
        mailpit.setPort(Integer.parseInt(System.getenv().getOrDefault("MAILPIT_TEST_PORT", "1025")));

        new NotificationEmailDeliveryWorker(
                new NotificationEmailOutboxStore(jdbc), mailpit, new ObjectMapper()).deliver();

        assertSent(outboxId);
    }

    private UUID insertPendingEmail() {
        UUID eventId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        jdbc.getJdbcTemplate().update("""
                INSERT INTO notification_events(
                    id,organization_id,source_service,source_event_id,event_type,category,importance,
                    heading,context,action_type,occurred_at)
                VALUES(?,901,'identity-service',?,'DEVELOPMENT_SAMPLE','GENERAL','NORMAL',
                    'Email check','Open Dawnrise for details','NONE',CURRENT_TIMESTAMP)
                """, eventId, "email-worker-" + eventId);
        jdbc.getJdbcTemplate().update("""
                INSERT INTO notification_recipients(id,organization_id,event_id,user_id)
                VALUES(?,901,?,901)
                """, recipientId, eventId);
        jdbc.getJdbcTemplate().update("""
                INSERT INTO notification_email_outbox(
                    id,organization_id,event_id,recipient_id,user_id,destination_email,template_key,
                    subject,template_variables,action_type,delivery_mode,message_id,status,available_at)
                VALUES(?,901,?,?,901,'recipient@example.test','SCHOOL_ANNOUNCEMENT',
                    'Email check','{"heading":"Email check","context":"Test delivery"}',
                    'NONE','IMMEDIATE',?,'PENDING',CURRENT_TIMESTAMP)
                """, outboxId, eventId, recipientId,
                "email-worker-message-" + outboxId);
        return outboxId;
    }

    private void assertSent(UUID outboxId) {
        assertThat(jdbc.getJdbcTemplate().queryForObject(
                "SELECT status FROM notification_email_outbox WHERE id=?", String.class, outboxId))
                .isEqualTo("SENT");
        assertThat(jdbc.getJdbcTemplate().queryForObject(
                "SELECT attempt_count FROM notification_email_outbox WHERE id=?", Integer.class, outboxId))
                .isEqualTo(1);
        assertThat(jdbc.getJdbcTemplate().queryForObject(
                "SELECT sent_at IS NOT NULL FROM notification_email_outbox WHERE id=?", Boolean.class, outboxId))
                .isTrue();
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
}
