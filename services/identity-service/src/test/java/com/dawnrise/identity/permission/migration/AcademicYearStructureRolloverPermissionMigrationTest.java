package com.dawnrise.identity.permission.migration;

import com.dawnrise.identity.permission.enums.PermissionCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicYearStructureRolloverPermissionMigrationTest {

    @Test
    void v41SeedsSensitiveRolloverPermissionForAdminAndPrincipalOnly()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V41__grant_academic_year_structure_rollover_permission.sql"
        ));

        assertThat(PermissionCode.values())
                .contains(PermissionCode.ACADEMIC_YEAR_STRUCTURE_ROLLOVER);
        assertThat(sql)
                .contains("'ACADEMIC_YEAR_STRUCTURE_ROLLOVER'")
                .contains("'academic-service'")
                .contains("TRUE")
                .contains("ON CONFLICT (code) DO NOTHING")
                .contains("ON CONFLICT (role, permission_id) DO NOTHING")
                .contains("('ADMIN')")
                .contains("('PRINCIPAL')")
                .doesNotContain("('VICE_PRINCIPAL')")
                .doesNotContain("('TEACHER')")
                .doesNotContain("('STUDENT')");
    }
}
