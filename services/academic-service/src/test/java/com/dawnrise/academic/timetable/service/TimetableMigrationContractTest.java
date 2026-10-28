package com.dawnrise.academic.timetable.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetableMigrationContractTest {
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V25__create_timetable_configuration_tables.sql");

    @Test
    void migrationHasPostgresOverlapAndTenantSafeReferences() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("CREATE EXTENSION IF NOT EXISTS btree_gist"));
        assertTrue(sql.contains("EXCLUDE USING gist"));
        assertTrue(sql.contains("int4range(start_minute, end_minute, '[)') WITH &&"));
        assertTrue(sql.contains("chk_timetable_bell_interval_minute_granularity"));
        assertTrue(sql.contains("ex_timetable_teacher_availability_overlap"));
        assertTrue(sql.contains("chk_timetable_schedule_assignment_grade_wing"));
        assertTrue(sql.contains("chk_timetable_eligibility_section_wing"));
        assertTrue(sql.contains("chk_timetable_grade_wing_alternate_eligibilities"));
        assertTrue(sql.contains("FOREIGN KEY (section_id, grade_level_id, academic_year_id, organization_id)"));
        assertTrue(sql.contains("FOREIGN KEY (grade_level_subject_id, grade_level_id, academic_year_id, organization_id)"));
        assertTrue(sql.contains("UNIQUE NULLS NOT DISTINCT"));
        assertTrue(sql.contains("FOREIGN KEY (wing_id, organization_id)\n        REFERENCES timetable_wings (id, organization_id)"));
        assertTrue(sql.contains("version BIGINT NOT NULL DEFAULT 0"));
    }

    @Test
    void alternateEligibilityDoesNotChangeCommittedTeacherAssignments() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("CREATE TABLE timetable_alternate_teacher_eligibilities"));
        assertTrue(!sql.contains("ALTER TABLE teacher_assignments"));
    }

    @Test
    void draftMaintenanceMigrationAddsIntentIndexesAndAuditAction() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V30__add_timetable_draft_intent_and_source.sql"));
        assertTrue(sql.contains("effective_from_intent DATE"));
        assertTrue(sql.contains("effective_to_exclusive_intent DATE"));
        assertTrue(sql.contains("idx_timetable_drafts_staff_list"));
        assertTrue(sql.contains("idx_timetable_generation_proposal_draft_lookup"));
        assertTrue(sql.contains("UPDATE_INTENT"));
    }
}
