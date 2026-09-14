package com.dawnrise.academic.studentattendance.reporting.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceReportingPropertiesTest {

    @Test
    void bindsFromDawnriseStudentAttendanceReportingPrefix() {
        MapConfigurationPropertySource source =
                new MapConfigurationPropertySource(Map.of(
                        "dawnrise.student-attendance.reporting.max-date-range-days",
                        "45",
                        "dawnrise.student-attendance.reporting.max-page-size",
                        "25",
                        "dawnrise.student-attendance.reporting.max-export-rows",
                        "5000"
                ));

        StudentAttendanceReportingProperties properties =
                new Binder(source)
                        .bind(
                                "dawnrise.student-attendance.reporting",
                                Bindable.of(
                                        StudentAttendanceReportingProperties.class
                                )
                        )
                        .orElseThrow(IllegalStateException::new);

        assertThat(properties.getMaxDateRangeDays()).isEqualTo(45);
        assertThat(properties.getMaxPageSize()).isEqualTo(25);
        assertThat(properties.getMaxExportRows()).isEqualTo(5000);
    }

    @Test
    void configurationValuesAreBeanValidationConstrained() {
        StudentAttendanceReportingProperties properties =
                new StudentAttendanceReportingProperties();
        properties.setMaxDateRangeDays(0);
        properties.setMaxPageSize(0);
        properties.setMaxExportRows(0);

        try (var validatorFactory =
                     Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator()
                    .validate(properties))
                    .hasSize(3);
        }
    }
}
