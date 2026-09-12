package com.dawnrise.identity.permission.migration;

import com.dawnrise.identity.permission.enums.PermissionCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceRecordingPermissionMigrationTest {

    private static final Path V45 = Path.of(
            "src/main/resources/db/migration/"
                    + "V45__add_student_attendance_recording_permissions.sql"
    );

    @Test
    void v45SeedsRecordingPermissionsWithConservativeRoles()
            throws Exception {
        String sql = Files.readString(V45);

        assertThat(sql)
                .contains(
                        "STUDENT_ATTENDANCE_VIEW",
                        "STUDENT_ATTENDANCE_RECORD",
                        "STUDENT_ATTENDANCE_SUBMIT",
                        "('ADMIN')",
                        "('PRINCIPAL')",
                        "('VICE_PRINCIPAL')",
                        "('TEACHER')",
                        "ON CONFLICT (code) DO NOTHING",
                        "ON CONFLICT (role, permission_id) DO NOTHING"
                )
                .doesNotContain(
                        "('STUDENT')",
                        "('PARENT')"
                );
    }

    @Test
    void permissionCodeEnumContainsRecordingCodes() {
        assertThat(PermissionCode.values())
                .contains(
                        PermissionCode.STUDENT_ATTENDANCE_VIEW,
                        PermissionCode.STUDENT_ATTENDANCE_RECORD,
                        PermissionCode.STUDENT_ATTENDANCE_SUBMIT
                );
    }
}
