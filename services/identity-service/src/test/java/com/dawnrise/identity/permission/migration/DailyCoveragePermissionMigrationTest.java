package com.dawnrise.identity.permission.migration;

import com.dawnrise.identity.permission.enums.PermissionCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyCoveragePermissionMigrationTest {
    @Test
    void migrationDefinesConservativeViewManageApproveAndAuditGrants() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V57__add_daily_coverage_permissions.sql"));
        for (String code : new String[]{"DAILY_COVERAGE_VIEW", "DAILY_COVERAGE_MANAGE",
                "DAILY_COVERAGE_APPROVE", "DAILY_COVERAGE_AUDIT_VIEW"}) {
            assertTrue(sql.contains(code));
            assertDoesNotThrow(() -> PermissionCode.valueOf(code));
        }
        assertTrue(sql.contains("('PRINCIPAL'),('VICE_PRINCIPAL')"));
        assertTrue(sql.contains("'DAILY_COVERAGE_APPROVE'"));
        assertTrue(sql.contains("'academic-service',TRUE,TRUE"));
    }
}
