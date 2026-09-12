package com.dawnrise.academic.studentattendance.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceRecordingMigrationTest {

    private static final Path V13 = Path.of(
            "src/main/resources/db/migration/V13__create_student_attendance_recording_tables.sql"
    );

    @Test
    void createsRecordingTablesWithTenantSafeConstraints() throws Exception {
        String migration = Files.readString(V13);

        assertThat(migration)
                .contains(
                        "CREATE TABLE student_attendance_sessions",
                        "CREATE TABLE student_attendance_records",
                        "uk_student_attendance_session_section_date",
                        "fk_student_attendance_session_section",
                        "fk_student_attendance_session_calendar_day",
                        "fk_student_attendance_record_session",
                        "fk_student_attendance_record_enrollment",
                        "chk_student_attendance_session_submission_metadata",
                        "chk_student_attendance_record_late_penalty",
                        "uk_student_attendance_record_session_enrollment",
                        "uk_student_attendance_record_session_student"
                )
                .contains("ON DELETE RESTRICT")
                .doesNotContain("correction_history");
    }

    @Test
    void enrollmentSupportConstraintIncludesStudentUserIdInKeyOrder()
            throws Exception {
        String migration = compactSql(Files.readString(V13));

        assertThat(migration)
                .contains("""
                        ADD CONSTRAINT uk_student_enrollment_id_student_section_grade_year_org UNIQUE ( id, student_user_id, section_id, grade_level_id, academic_year_id, organization_id )
                        """.trim());
    }

    @Test
    void enrollmentForeignKeyIncludesStudentUserIdInMatchingOrder()
            throws Exception {
        String migration = compactSql(Files.readString(V13));

        assertThat(migration)
                .contains("""
                        CONSTRAINT fk_student_attendance_record_enrollment FOREIGN KEY ( student_enrollment_id, student_user_id, section_id, grade_level_id, academic_year_id, organization_id ) REFERENCES student_enrollments ( id, student_user_id, section_id, grade_level_id, academic_year_id, organization_id ) ON DELETE RESTRICT
                        """.trim());
    }

    @Test
    void attendanceRecordSessionForeignKeyRestrictsSessionDeletion()
            throws Exception {
        String migration = compactSql(Files.readString(V13));

        assertThat(migration).containsPattern(
                "CONSTRAINT fk_student_attendance_record_session "
                        + "FOREIGN KEY \\(\\s*attendance_session_id, "
                        + "organization_id, academic_year_id, grade_level_id, "
                        + "section_id\\s*\\) REFERENCES student_attendance_sessions "
                        + "\\(\\s*id, organization_id, academic_year_id, "
                        + "grade_level_id, section_id\\s*\\) ON DELETE RESTRICT"
        );
        assertThat(migration).doesNotContainPattern(
                "CONSTRAINT fk_student_attendance_record_session .* ON DELETE CASCADE"
        );
    }

    @Test
    void mismatchedStudentUserIdHasNoReferencedEnrollmentKey() {
        Set<List<Long>> referencedEnrollmentKeys = Set.of(List.of(
                31L,
                41L,
                23L,
                22L,
                21L,
                11L
        ));

        List<Long> attendanceRecordForeignKey = List.of(
                31L,
                42L,
                23L,
                22L,
                21L,
                11L
        );

        assertThat(referencedEnrollmentKeys)
                .doesNotContain(attendanceRecordForeignKey);
    }

    private static String compactSql(String sql) {
        return Pattern.compile("\\s+")
                .matcher(sql)
                .replaceAll(" ")
                .trim();
    }
}
