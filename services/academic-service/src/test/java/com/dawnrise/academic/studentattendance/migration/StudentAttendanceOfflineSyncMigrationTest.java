package com.dawnrise.academic.studentattendance.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceOfflineSyncMigrationTest {

    @Test
    void v16CreatesDurableTenantSafeIdempotencyOperations() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V16__create_student_attendance_offline_sync_operations.sql"
        ));

        assertThat(sql).contains(
                "CREATE TABLE student_attendance_offline_sync_operations",
                "UNIQUE (organization_id, actor_user_id, idempotency_key)",
                "request_hash VARCHAR(71) NOT NULL",
                "result_json JSONB",
                "status IN ('RUNNING', 'SUCCEEDED', 'FAILED')",
                "fk_student_attendance_offline_sync_academic_year",
                "fk_student_attendance_offline_sync_section",
                "fk_student_attendance_offline_sync_session",
                "FOREIGN KEY (\n            attendance_session_id,\n            organization_id,\n            academic_year_id,\n            grade_level_id,\n            section_id\n        )",
                "REFERENCES student_attendance_sessions (\n                id,\n                organization_id,\n                academic_year_id,\n                grade_level_id,\n                section_id\n            )",
                "ON DELETE RESTRICT"
        );
        assertThat(sql).doesNotContain("ON DELETE CASCADE");
    }
}
