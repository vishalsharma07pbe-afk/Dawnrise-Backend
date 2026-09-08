package com.dawnrise.academic.academicyearrollover.entity;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicYearStructureRolloverOperationTest {

    @Test
    void resultJsonIsMappedAsPostgresJsonb() throws Exception {
        Field resultJson =
                AcademicYearStructureRolloverOperation.class
                        .getDeclaredField("resultJson");

        JdbcTypeCode jdbcTypeCode =
                resultJson.getAnnotation(JdbcTypeCode.class);
        Column column = resultJson.getAnnotation(Column.class);

        assertThat(jdbcTypeCode).isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("result_json");
        assertThat(column.columnDefinition()).isEqualTo("jsonb");
    }
}
