package com.dawnrise.identity.permission.migration;

import com.dawnrise.identity.permission.enums.PermissionCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceCorrectionPermissionMigrationTest {

    private static final Path V46 = Path.of(
            "src/main/resources/db/migration/"
                    + "V46__add_student_attendance_correction_permissions.sql"
    );

    @Test
    void v46SeedsCorrectionPermissionsWithConservativeRoles() throws Exception {
        String sql = Files.readString(V46);

        assertThat(sql).contains(
                "STUDENT_ATTENDANCE_CORRECTION_REQUEST",
                "STUDENT_ATTENDANCE_CORRECTION_VIEW",
                "STUDENT_ATTENDANCE_CORRECTION_APPROVE",
                "('TEACHER')",
                "('ADMIN')",
                "('PRINCIPAL')",
                "('VICE_PRINCIPAL')",
                "ON CONFLICT (code) DO NOTHING",
                "ON CONFLICT (role, permission_id) DO NOTHING"
        );
        assertThat(sql).doesNotContain(
                "('STUDENT')",
                "('PARENT')"
        );
    }

    @Test
    void permissionCodeEnumContainsCorrectionCodes() {
        assertThat(PermissionCode.values()).contains(
                PermissionCode.STUDENT_ATTENDANCE_CORRECTION_REQUEST,
                PermissionCode.STUDENT_ATTENDANCE_CORRECTION_VIEW,
                PermissionCode.STUDENT_ATTENDANCE_CORRECTION_APPROVE
        );
    }
}
