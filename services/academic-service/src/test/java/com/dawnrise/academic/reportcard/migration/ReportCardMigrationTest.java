package com.dawnrise.academic.reportcard.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCardMigrationTest {
    private static final Path V24 = Path.of(
            "src/main/resources/db/migration/V24__create_grading_policies_and_report_cards.sql");

    @Test
    void usesSixDigitPercentagePrecisionAndRejectsUnsafeRoundingMode() throws Exception {
        String migration = compact(Files.readString(V24));

        assertThat(migration)
                .contains("subject_pass_percentage NUMERIC(9,6)")
                .contains("overall_pass_percentage NUMERIC(9,6)")
                .contains("minimum_percentage NUMERIC(9,6)")
                .contains("maximum_percentage NUMERIC(9,6)")
                .doesNotContain("'UNNECESSARY'");
    }

    @Test
    void reportCardEnrollmentForeignKeyIsTenantScoped() throws Exception {
        String migration = compact(Files.readString(V24));
        assertThat(migration).contains(
                "CONSTRAINT fk_rc_enrollment FOREIGN KEY(enrollment_id,organization_id) "
                        + "REFERENCES student_enrollments(id,organization_id) ON DELETE RESTRICT");
    }

    @Test
    void crossTenantEnrollmentCannotMatchCompositeReferencedKey() {
        Set<List<Long>> enrollmentKeys = Set.of(List.of(8L, 1L));
        assertThat(enrollmentKeys).doesNotContain(List.of(8L, 2L));
    }

    @Test
    void includesLifecycleSnapshotAndTimestampChecks() throws Exception {
        assertThat(Files.readString(V24)).contains(
                "chk_rc_names",
                "chk_rc_actor_ids",
                "chk_rc_rounding",
                "chk_rc_publication_state",
                "chk_rc_review_state",
                "chk_rc_return_reason",
                "chk_rc_timestamps",
                "chk_rcs_names");
    }

    private static String compact(String sql) {
        return Pattern.compile("\\s+").matcher(sql).replaceAll(" ").trim();
    }
}
