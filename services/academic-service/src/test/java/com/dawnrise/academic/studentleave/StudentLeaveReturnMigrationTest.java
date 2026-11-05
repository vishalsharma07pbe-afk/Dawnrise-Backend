package com.dawnrise.academic.studentleave;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentLeaveReturnMigrationTest {
    private Path temporaryDirectory;
    private Path dataDirectory;
    private Path socketDirectory;
    private Path pgCtl;
    private String jdbcUrl;

    @BeforeAll
    void startDisposablePostgres() throws Exception {
        Path initDb = executable("initdb");
        pgCtl = executable("pg_ctl");
        assumeTrue(initDb != null && pgCtl != null, "PostgreSQL 16 binaries are required");
        temporaryDirectory = Files.createTempDirectory("dawnrise-student-leave-v39-");
        dataDirectory = temporaryDirectory.resolve("data");
        run(initDb.toString(), "-D", dataDirectory.toString(), "-A", "trust", "-U", "postgres",
                "--no-locale", "--encoding=UTF8");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        socketDirectory = Files.createDirectory(Path.of("/tmp", "drsl-" + port));
        run(pgCtl.toString(), "-D", dataDirectory.toString(), "-l",
                temporaryDirectory.resolve("postgres.log").toString(), "-o",
                "-p " + port + " -k " + socketDirectory + " -F", "-w", "start");
        jdbcUrl = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
    }

    @AfterAll
    void stopDisposablePostgres() throws Exception {
        if (pgCtl != null && dataDirectory != null && Files.exists(dataDirectory.resolve("postmaster.pid"))) {
            run(pgCtl.toString(), "-D", dataDirectory.toString(), "-m", "immediate", "-w", "stop");
        }
        if (temporaryDirectory != null && Files.exists(temporaryDirectory)) {
            try (var paths = Files.walk(temporaryDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        }
        if (socketDirectory != null) Files.deleteIfExists(socketDirectory);
    }

    @Test
    void flywayUpgradesAppliedV38AndDraftCanBeRead() throws Exception {
        Flyway.configure().dataSource(jdbcUrl, "postgres", "")
                .locations("filesystem:src/main/resources/db/migration").target("38").load().migrate();
        Flyway.configure().dataSource(jdbcUrl, "postgres", "")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();

        try (var connection = DriverManager.getConnection(jdbcUrl, "postgres", "");
             var statement = connection.createStatement()) {
            try (var versions = statement.executeQuery(
                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")) {
                versions.next();
                assertEquals("39", versions.getString(1));
            }
            statement.executeUpdate("""
                    INSERT INTO student_leave_requests
                        (organization_id, student_user_id, initiator_user_id, initiator_type,
                         academic_year_id, grade_level_id, section_id)
                    VALUES (1, 15, 20, 'GUARDIAN', 1, 1, 1)
                    """);
            try (var draft = statement.executeQuery(
                    "SELECT status, returned_by_user_id, return_comment FROM student_leave_requests WHERE student_user_id=15")) {
                draft.next();
                assertEquals("DRAFT", draft.getString(1));
                assertEquals(null, draft.getObject(2));
                assertEquals(null, draft.getString(3));
            }
            statement.executeUpdate("""
                    UPDATE student_leave_requests SET status='PENDING_CORRECTION',
                        returned_by_user_id=30, return_comment='Please correct the dates'
                    WHERE student_user_id=15
                    """);
            try (var returned = statement.executeQuery(
                    "SELECT returned_by_user_id, return_comment FROM student_leave_requests WHERE student_user_id=15")) {
                returned.next();
                assertEquals(30L, returned.getLong(1));
                assertEquals("Please correct the dates", returned.getString(2));
            }
        }
    }

    private Path executable(String name) throws Exception {
        Process process = new ProcessBuilder("/usr/bin/env", "sh", "-c", "command -v " + name).start();
        String result = new String(process.getInputStream().readAllBytes()).trim();
        return process.waitFor() == 0 && !result.isBlank() ? Path.of(result) : null;
    }

    private void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), () -> String.join(" ", command) + " failed:\n" + output);
    }
}
