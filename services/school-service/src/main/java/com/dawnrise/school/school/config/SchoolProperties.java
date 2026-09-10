package com.dawnrise.school.school.config;

import com.dawnrise.school.school.exception.InvalidSchoolTimeZoneException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DateTimeException;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "dawnrise.school")
public class SchoolProperties {

    private String defaultTimeZone;

    public String getDefaultTimeZone() {
        return defaultTimeZone;
    }

    public void setDefaultTimeZone(String defaultTimeZone) {
        validateTimeZone(defaultTimeZone);
        this.defaultTimeZone = defaultTimeZone.trim();
    }

    public ZoneId defaultZoneId() {
        return ZoneId.of(defaultTimeZone);
    }

    private void validateTimeZone(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidSchoolTimeZoneException(
                    "dawnrise.school.default-time-zone must not be blank"
            );
        }

        if (value.trim().length() > 64) {
            throw new InvalidSchoolTimeZoneException(
                    "dawnrise.school.default-time-zone cannot exceed 64 characters"
            );
        }

        try {
            ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            throw new InvalidSchoolTimeZoneException(
                    "dawnrise.school.default-time-zone must be a valid IANA time zone",
                    exception
            );
        }
    }
}
