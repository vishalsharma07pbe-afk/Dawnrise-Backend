package com.dawnrise.academic.studentattendance.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceImportMigrationTest {

    @Test
    void v17CreatesTenantSafeDurableImportPreviews() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V17__create_student_attendance_import_previews.sql"
        ));

        assertThat(sql).contains(
                "CREATE TABLE student_attendance_import_previews",
                "payload_json JSONB NOT NULL",
                "row_count >= 1 AND row_count <= 100",
                "preview_fingerprint ~ '^sha256:[0-9a-f]{64}$'",
                "FOREIGN KEY (section_id, grade_level_id, academic_year_id, organization_id)",
                "FOREIGN KEY (confirmed_sync_operation_id, organization_id, actor_user_id)",
                "ON DELETE RESTRICT"
        ).doesNotContain("ON DELETE CASCADE");
    }
}
