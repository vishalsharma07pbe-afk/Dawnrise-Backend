package com.dawnrise.school.school.config;

import com.dawnrise.school.school.exception.InvalidSchoolTimeZoneException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchoolPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            PropertiesConfig.class
                    );

    @Test
    void getDefaultTimeZone_hasNoJavaFallback() {
        SchoolProperties properties = new SchoolProperties();

        assertNull(properties.getDefaultTimeZone());
    }

    @Test
    void setDefaultTimeZone_acceptsValidIanaZone() {
        SchoolProperties properties = new SchoolProperties();

        properties.setDefaultTimeZone("Europe/London");

        assertEquals("Europe/London", properties.getDefaultTimeZone());
        assertEquals(
                ZoneId.of("Europe/London"),
                properties.defaultZoneId()
        );
    }

    @Test
    void setDefaultTimeZone_rejectsInvalidValue() {
        SchoolProperties properties = new SchoolProperties();

        InvalidSchoolTimeZoneException exception = assertThrows(
                InvalidSchoolTimeZoneException.class,
                () -> properties.setDefaultTimeZone("IST")
        );

        assertEquals(
                "dawnrise.school.default-time-zone must be a valid IANA time zone",
                exception.getMessage()
        );
    }

    @Test
    void contextBinding_succeedsWithValidTimeZone() {
        contextRunner
                .withPropertyValues(
                        "dawnrise.school.default-time-zone=Asia/Kolkata"
                )
                .run(context -> {
                    SchoolProperties properties =
                            context.getBean(SchoolProperties.class);

                    assertEquals(
                            "Asia/Kolkata",
                            properties.getDefaultTimeZone()
                    );
                });
    }

    @Test
    void contextBinding_failsWithInvalidTimeZone() {
        contextRunner
                .withPropertyValues(
                        "dawnrise.school.default-time-zone=IST"
                )
                .run(context ->
                        org.assertj.core.api.Assertions.assertThat(
                                        context.getStartupFailure()
                                )
                                .hasStackTraceContaining(
                                        "dawnrise.school.default-time-zone must be a valid IANA time zone"
                                )
                );
    }

    @Configuration
    @EnableConfigurationProperties(SchoolProperties.class)
    static class PropertiesConfig {
    }
}
