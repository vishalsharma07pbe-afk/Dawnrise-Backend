package com.dawnrise.school.school.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolTimeZoneMigrationTest {

    @Test
    void v9AddsNonNullSchoolTimeZoneColumnWithDefault()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V9__add_school_time_zone.sql"
        ));

        assertThat(sql)
                .contains("ADD COLUMN time_zone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata'")
                .contains("chk_school_time_zone_not_blank")
                .contains("CHECK (BTRIM(time_zone_id) <> '')");
    }
}
