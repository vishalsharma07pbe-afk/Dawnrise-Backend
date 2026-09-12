package com.dawnrise.academic.studentattendance.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceCorrectionMigrationTest {

    private static final Path V15 = Path.of(
            "src/main/resources/db/migration/V15__create_student_attendance_correction_tables.sql"
    );

    @Test
    void v15CreatesCorrectionTablesWithStrictHistoryAndTenantSafeConstraints() throws Exception {
        String sql = Files.readString(V15);

        assertThat(sql).contains(
                "CREATE TABLE student_attendance_correction_requests",
                "CREATE TABLE student_attendance_correction_items",
                "uk_student_attendance_correction_pending_session",
                "WHERE status = 'PENDING'",
                "fk_student_attendance_correction_request_session",
                "fk_student_attendance_correction_item_record",
                "ON DELETE RESTRICT",
                "status = 'PENDING'\n                AND reviewed_by_user_id IS NULL\n                AND reviewed_at IS NULL\n                AND review_comment IS NULL",
                "status = 'CANCELLED'\n                AND reviewed_by_user_id IS NULL\n                AND reviewed_at IS NOT NULL",
                "status = 'APPROVED'\n                AND reviewed_by_user_id IS NOT NULL\n                AND reviewed_at IS NOT NULL\n                AND reviewed_by_user_id <> requested_by_user_id",
                "status = 'REJECTED'\n                AND reviewed_by_user_id IS NOT NULL\n                AND reviewed_at IS NOT NULL\n                AND reviewed_by_user_id <> requested_by_user_id\n                AND review_comment IS NOT NULL\n                AND BTRIM(review_comment) <> ''",
                "reviewed_by_user_id <> requested_by_user_id",
                "expected_attendance_record_version",
                "previous_recorded_status",
                "proposed_effective_status",
                "chk_student_attendance_correction_item_proposed_late_penalty",
                "chk_student_attendance_correction_item_previous_credits",
                "chk_student_attendance_correction_item_changes_snapshot",
                "proposed_remarks IS DISTINCT FROM previous_remarks"
        );
        assertThat(sql).doesNotContain("ON DELETE CASCADE");
    }
}
