package com.dawnrise.academic.studentattendance.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceLatePenaltyMigrationTest {

    @Test
    void v12AddsLatePenaltyPolicyColumnsAndConstraintsOnly()
            throws Exception {
        Path v11Path = Path.of(
                "src/main/resources/db/migration/V11__create_student_attendance_policy_and_calendar_tables.sql"
        );
        Path v12Path = Path.of(
                "src/main/resources/db/migration/V12__add_student_attendance_late_penalty_policy.sql"
        );

        assertThat(Files.exists(v11Path)).isTrue();
        assertThat(Files.exists(v12Path)).isTrue();

        String v11 = Files.readString(v11Path);
        String v12 = Files.readString(v12Path);

        assertThat(v11).doesNotContain(
                "late_penalty_enabled",
                "late_occurrences_threshold",
                "late_penalty_outcome",
                "late_counting_period"
        );
        assertThat(v12).contains(
                "ADD COLUMN late_penalty_enabled BOOLEAN NOT NULL DEFAULT FALSE",
                "ADD COLUMN late_occurrences_threshold INTEGER NOT NULL DEFAULT 3",
                "ADD COLUMN late_penalty_outcome VARCHAR(20) NOT NULL DEFAULT 'HALF_DAY'",
                "ADD COLUMN late_counting_period VARCHAR(20) NOT NULL DEFAULT 'MONTHLY'",
                "CONSTRAINT chk_student_attendance_policy_late_threshold",
                "late_occurrences_threshold >= 1",
                "late_occurrences_threshold <= 100",
                "CONSTRAINT chk_student_attendance_policy_late_outcome",
                "late_penalty_outcome IN ('HALF_DAY', 'ABSENT')",
                "CONSTRAINT chk_student_attendance_policy_late_counting_period",
                "late_counting_period IN ('MONTHLY')",
                "ALTER COLUMN late_penalty_enabled DROP DEFAULT",
                "ALTER COLUMN late_occurrences_threshold DROP DEFAULT",
                "ALTER COLUMN late_penalty_outcome DROP DEFAULT",
                "ALTER COLUMN late_counting_period DROP DEFAULT"
        );
        assertThat(v12).doesNotContain("CREATE INDEX");
        assertThat(Pattern
                .compile("\\blate_penalty_enabled\\b")
                .matcher(v12)
                .results()
                .count()
        ).isEqualTo(2);
    }
}
