package com.dawnrise.academic.dailycoverage;

import com.dawnrise.academic.dailycoverage.controller.DailyCoverageController;
import com.dawnrise.academic.dailycoverage.controller.DailyTimetableController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyCoverageContractTest {
    @Test
    void migrationProtectsTenantResourcesAndPendingIsDistinctFromApproved() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V34__create_daily_timetable_cover.sql"));
        assertTrue(sql.contains("FOREIGN KEY (publication_entry_id, organization_id, academic_year_id, publication_id)"));
        assertTrue(sql.contains("ex_timetable_teacher_absence_overlap"));
        assertTrue(sql.contains("ex_timetable_daily_cover_teacher_overlap"));
        assertTrue(sql.contains("ex_timetable_daily_cover_room_overlap"));
        assertTrue(sql.contains("ex_timetable_daily_cover_section_overlap"));
        assertTrue(sql.contains("status IN ('AWAITING_APPROVAL','APPROVED')"));
        assertTrue(sql.contains("uk_timetable_daily_cover_outbox_idempotency"));
        assertTrue(sql.contains("FOREIGN KEY (activity_plan_id, organization_id)"));
    }

    @Test
    void staffOperationsAreTenantAndPermissionGuarded() {
        for (Method method : DailyCoverageController.class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.getName().equals("org") || method.getName().equals("actor")) continue;
            PreAuthorize rule = method.getAnnotation(PreAuthorize.class);
            assertNotNull(rule, method.getName());
            assertTrue(rule.value().contains("hasOrganization(authentication)"), method.getName());
            assertTrue(rule.value().contains("DAILY_COVERAGE_"), method.getName());
        }
    }

    @Test
    void dailySelfViewsUseJwtScopedPermissions() {
        for (String name : Arrays.asList("teacherSelf", "studentSelf", "parent")) {
            Method method = Arrays.stream(DailyTimetableController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            PreAuthorize rule = method.getAnnotation(PreAuthorize.class);
            assertNotNull(rule);
            assertTrue(rule.value().contains("hasOrganization(authentication)"));
            assertTrue(rule.value().contains("TIMETABLE_PUBLISHED_VIEW_SELF"));
        }
    }
}
