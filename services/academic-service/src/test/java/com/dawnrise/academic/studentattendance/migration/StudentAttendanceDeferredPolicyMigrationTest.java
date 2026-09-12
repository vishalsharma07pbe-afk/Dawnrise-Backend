package com.dawnrise.academic.studentattendance.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceDeferredPolicyMigrationTest {

    @Test
    void v14BackfillsConstrainsAndDropsTemporaryDefaults() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V14__extend_student_attendance_policy_deferred_submission.sql"
        ));

        assertThat(Pattern
                .compile("chk_student_attendance_policy_back_entry_days")
                .matcher(migration)
                .results()
                .count()
        ).isEqualTo(1);
        assertThat(migration).contains(
                "ADD COLUMN deferred_entry_enabled BOOLEAN NOT NULL DEFAULT TRUE",
                "ADD COLUMN teacher_back_entry_days INTEGER NOT NULL DEFAULT 0",
                "ADD COLUMN leadership_back_entry_days INTEGER NOT NULL DEFAULT 30",
                "ADD COLUMN automatic_submission_enabled BOOLEAN NOT NULL DEFAULT FALSE",
                "teacher_back_entry_days >= 0",
                "teacher_back_entry_days <= 365",
                "leadership_back_entry_days >= teacher_back_entry_days",
                "leadership_back_entry_days <= 365",
                "ALTER COLUMN deferred_entry_enabled DROP DEFAULT",
                "ALTER COLUMN teacher_back_entry_days DROP DEFAULT",
                "ALTER COLUMN leadership_back_entry_days DROP DEFAULT",
                "ALTER COLUMN automatic_submission_enabled DROP DEFAULT"
        );
    }
}
