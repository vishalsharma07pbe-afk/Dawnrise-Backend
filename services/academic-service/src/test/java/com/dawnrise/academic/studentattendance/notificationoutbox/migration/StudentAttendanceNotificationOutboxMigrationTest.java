package com.dawnrise.academic.studentattendance.notificationoutbox.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceNotificationOutboxMigrationTest {

    @Test
    void v18DefinesTenantSafeOutboxConstraintsAndIndexes()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V18__create_student_attendance_notification_outbox.sql"
        ));

        assertThat(sql)
                .contains("CREATE TABLE student_attendance_notification_outbox")
                .contains("UNIQUE (event_id)")
                .contains("UNIQUE (organization_id, idempotency_key)")
                .contains("JSONB_TYPEOF(payload) = 'object'")
                .contains("'ABSENCE_RECORDED'")
                .contains("'LATE_RECORDED'")
                .contains("'LATE_PENALTY_APPLIED'")
                .contains("'ATTENDANCE_ALERT_CLEARED'")
                .contains("'MANUAL_SUBMISSION'")
                .contains("'AUTOMATIC_SUBMISSION'")
                .contains("'CORRECTION_APPROVAL'")
                .contains("'PENDING'")
                .contains("'PUBLISHED'")
                .contains("'DEAD_LETTER'")
                .contains("fk_student_attendance_notification_outbox_record")
                .contains("attendance_record_id")
                .contains("organization_id")
                .contains("academic_year_id")
                .contains("grade_level_id")
                .contains("section_id")
                .contains("attendance_session_id")
                .contains("student_enrollment_id")
                .contains("student_user_id")
                .contains("fk_student_attendance_notification_outbox_correction")
                .contains("event_source = 'CORRECTION_APPROVAL'")
                .contains("correction_request_id IS NOT NULL")
                .contains("idx_student_attendance_notification_outbox_pending")
                .contains("WHERE publication_status = 'PENDING'");
    }
}
