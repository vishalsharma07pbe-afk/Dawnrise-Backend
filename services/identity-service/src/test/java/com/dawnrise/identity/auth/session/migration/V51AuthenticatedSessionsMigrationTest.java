package com.dawnrise.identity.auth.session.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V51AuthenticatedSessionsMigrationTest {

    private static final OffsetDateTime AUTHENTICATED_AT =
            OffsetDateTime.parse("2026-10-17T13:32:07Z");

    @Test
    void migratesVersion50ParentSessionsWithoutViolatingTimestampConstraints()
            throws Exception {
        DataSource dataSource = version50Schema();
        UUID validId = UUID.randomUUID();
        UUID earlyActivityId = UUID.randomUUID();
        UUID earlyLockId = UUID.randomUUID();
        OffsetDateTime validActivity = AUTHENTICATED_AT.plusMinutes(5);
        OffsetDateTime validLock = AUTHENTICATED_AT.plusMinutes(10);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id) VALUES (1), (2), (3)
                    """);
            insertParentSession(
                    statement,
                    validId,
                    1L,
                    AUTHENTICATED_AT,
                    validActivity,
                    validLock
            );
            insertParentSession(
                    statement,
                    earlyActivityId,
                    2L,
                    AUTHENTICATED_AT,
                    AUTHENTICATED_AT.minusMinutes(14),
                    null
            );
            insertParentSession(
                    statement,
                    earlyLockId,
                    3L,
                    AUTHENTICATED_AT,
                    AUTHENTICATED_AT.plusMinutes(1),
                    AUTHENTICATED_AT.minusMinutes(2)
            );
        }

        new ResourceDatabasePopulator(
                migrationForH2()
        ).execute(dataSource);

        assertSessionTimestamps(
                dataSource,
                validId,
                AUTHENTICATED_AT,
                validActivity,
                validLock
        );
        assertSessionTimestamps(
                dataSource,
                earlyActivityId,
                AUTHENTICATED_AT,
                AUTHENTICATED_AT,
                null
        );
        assertSessionTimestamps(
                dataSource,
                earlyLockId,
                AUTHENTICATED_AT,
                AUTHENTICATED_AT.plusMinutes(1),
                AUTHENTICATED_AT
        );

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM authenticated_sessions
                     WHERE last_activity_at < authenticated_at
                        OR (locked_at IS NOT NULL AND locked_at < authenticated_at)
                        OR (retired_at IS NOT NULL AND retired_at < authenticated_at)
                        OR version < 0
                        OR user_id IS NULL
                        OR platform_user_id IS NOT NULL
                     """)) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    private ByteArrayResource migrationForH2() throws Exception {
        String migration = new ClassPathResource(
                "db/migration/V51__create_authenticated_sessions.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        return new ByteArrayResource(
                migration.replace("ON CONFLICT (id) DO NOTHING", "")
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private DataSource version50Schema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:v51-authenticated-sessions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, status VARCHAR(32) DEFAULT 'ACTIVE')");
            statement.execute("CREATE TABLE platform_users (id BIGINT PRIMARY KEY, status VARCHAR(32) DEFAULT 'ACTIVE')");
            statement.execute("""
                    CREATE TABLE parent_sessions (
                        id UUID PRIMARY KEY,
                        user_id BIGINT NOT NULL REFERENCES users(id),
                        authenticated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        locked_at TIMESTAMP WITH TIME ZONE,
                        version BIGINT NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE refresh_tokens (
                        token_family_id UUID NOT NULL,
                        user_id BIGINT NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        revoked_at TIMESTAMP WITH TIME ZONE,
                        expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        family_expires_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE platform_refresh_tokens (
                        token_family_id UUID NOT NULL,
                        platform_user_id BIGINT NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        revoked_at TIMESTAMP WITH TIME ZONE,
                        expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        family_expires_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
        }
        return dataSource;
    }

    private void insertParentSession(
            Statement statement,
            UUID id,
            Long userId,
            OffsetDateTime authenticatedAt,
            OffsetDateTime lastActivityAt,
            OffsetDateTime lockedAt
    ) throws Exception {
        String lockedValue = lockedAt == null
                ? "NULL"
                : "TIMESTAMP WITH TIME ZONE '" + lockedAt + "'";
        statement.executeUpdate("""
                INSERT INTO parent_sessions (
                    id,
                    user_id,
                    authenticated_at,
                    last_activity_at,
                    locked_at,
                    version
                ) VALUES (
                    '%s',
                    %d,
                    TIMESTAMP WITH TIME ZONE '%s',
                    TIMESTAMP WITH TIME ZONE '%s',
                    %s,
                    0
                )
                """.formatted(
                id,
                userId,
                authenticatedAt,
                lastActivityAt,
                lockedValue
        ));
    }

    private void assertSessionTimestamps(
            DataSource dataSource,
            UUID id,
            OffsetDateTime expectedAuthenticatedAt,
            OffsetDateTime expectedLastActivityAt,
            OffsetDateTime expectedLockedAt
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT authenticated_at, last_activity_at, locked_at
                     FROM authenticated_sessions
                     WHERE id = '%s'
                     """.formatted(id))) {
            assertTrue(result.next());
            assertEquals(
                    expectedAuthenticatedAt,
                    result.getObject("authenticated_at", OffsetDateTime.class)
            );
            assertEquals(
                    expectedLastActivityAt,
                    result.getObject("last_activity_at", OffsetDateTime.class)
            );
            if (expectedLockedAt == null) {
                assertNull(result.getObject("locked_at"));
            } else {
                assertEquals(
                        expectedLockedAt,
                        result.getObject("locked_at", OffsetDateTime.class)
                );
            }
        }
    }
}
