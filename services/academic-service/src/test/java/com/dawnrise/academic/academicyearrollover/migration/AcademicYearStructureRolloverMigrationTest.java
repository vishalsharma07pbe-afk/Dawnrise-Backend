package com.dawnrise.academic.academicyearrollover.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicYearStructureRolloverMigrationTest {

    @Test
    void v9CreatesRolloverOperationTableWithTenantSafeConstraints()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V9__create_academic_year_structure_rollover_operations_table.sql"
        ));

        assertThat(sql)
                .contains("CREATE TABLE academic_year_structure_rollover_operations")
                .contains("UNIQUE (organization_id, idempotency_key)")
                .contains("CHECK (source_academic_year_id <> target_academic_year_id)")
                .contains("FOREIGN KEY (source_academic_year_id, organization_id)")
                .contains("FOREIGN KEY (target_academic_year_id, organization_id)")
                .contains("'PENDING'")
                .contains("'RUNNING'")
                .contains("'SUCCEEDED'")
                .contains("'FAILED'")
                .contains("'STALE_PREVIEW'")
                .contains("'CONFLICTED'")
                .contains("idx_rollover_operation_organization_target_status")
                .contains("idx_rollover_operation_organization_source_target")
                .contains("idx_rollover_operation_organization_user_created");
    }
}
