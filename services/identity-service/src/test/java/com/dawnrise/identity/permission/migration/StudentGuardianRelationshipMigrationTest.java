package com.dawnrise.identity.permission.migration;

import com.dawnrise.identity.permission.enums.PermissionCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentGuardianRelationshipMigrationTest {

    @Test
    void v43CreatesRelationshipTableAndSeedsPermissions()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V43__create_student_guardian_relationships.sql"
        ));

        assertThat(PermissionCode.values())
                .contains(
                        PermissionCode.STUDENT_GUARDIAN_RELATIONSHIP_VIEW,
                        PermissionCode.STUDENT_GUARDIAN_RELATIONSHIP_MANAGE
                );

        assertThat(sql)
                .contains("ADD CONSTRAINT uk_users_id_organization")
                .contains("CREATE TABLE student_guardian_relationships")
                .contains("FOREIGN KEY (student_user_id, organization_id)")
                .contains("FOREIGN KEY (guardian_user_id, organization_id)")
                .contains("FOREIGN KEY (created_by_user_id, organization_id)")
                .contains("FOREIGN KEY (ended_by_user_id, organization_id)")
                .contains("uk_student_guardian_relationships_active_pair")
                .contains("uk_student_guardian_relationships_active_primary")
                .contains("WHERE status = 'ACTIVE'")
                .contains("AND primary_guardian = TRUE")
                .contains("'STUDENT_GUARDIAN_RELATIONSHIP_VIEW'")
                .contains("'STUDENT_GUARDIAN_RELATIONSHIP_MANAGE'")
                .contains("'identity-service'")
                .contains("('ADMIN')")
                .contains("('PRINCIPAL')")
                .contains("('VICE_PRINCIPAL')")
                .contains("('ADMISSIONS_OFFICER')")
                .contains("ON CONFLICT (code) DO NOTHING")
                .contains("ON CONFLICT (role, permission_id) DO NOTHING");
    }
}
