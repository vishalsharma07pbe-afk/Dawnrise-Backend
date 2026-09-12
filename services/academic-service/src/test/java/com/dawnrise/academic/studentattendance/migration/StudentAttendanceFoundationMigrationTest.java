package com.dawnrise.academic.studentattendance.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceFoundationMigrationTest {

    @Test
    void v11CreatesOnlyPolicyAndCalendarFoundationTables() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V11__create_student_attendance_policy_and_calendar_tables.sql"
        ));

        assertThat(migration).contains(
                "CREATE TABLE student_attendance_policies",
                "CREATE TABLE student_attendance_status_policies",
                "CREATE TABLE academic_calendar_days",
                "CONSTRAINT uk_academic_calendar_day_date",
                "CREATE INDEX idx_academic_calendar_day_requirement",
                "day_type = 'EXAM_DAY'\n"
                        + "                AND attendance_requirement IN",
                "REFERENCES academic_years (id, organization_id)",
                "ON DELETE RESTRICT"
        );
        assertThat(migration).doesNotContain(
                "CREATE INDEX idx_academic_calendar_day_year_date"
        );
        assertThat(Pattern
                .compile("CREATE INDEX idx_academic_calendar_day_")
                .matcher(migration)
                .results()
                .count()
        ).isEqualTo(1);
        assertThat(migration).doesNotContain(
                "attendance_sessions",
                "attendance_records",
                "delegations",
                "corrections"
        );
    }
}
