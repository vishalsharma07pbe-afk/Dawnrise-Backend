package com.dawnrise.school.school.entity;

import com.dawnrise.school.school.exception.InvalidSchoolTimeZoneException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchoolTimeZoneTest {

    @Test
    void applyTimeZone_acceptsAsiaKolkata() {
        School school = new School();

        school.applyTimeZone("Asia/Kolkata");

        assertEquals("Asia/Kolkata", school.getTimeZoneId());
    }

    @Test
    void applyTimeZone_acceptsAnotherIanaZone() {
        School school = new School();

        school.applyTimeZone("Europe/London");

        assertEquals("Europe/London", school.getTimeZoneId());
    }

    @Test
    void applyTimeZone_rejectsBlankValue() {
        School school = new School();

        InvalidSchoolTimeZoneException exception = assertThrows(
                InvalidSchoolTimeZoneException.class,
                () -> school.applyTimeZone(" ")
        );

        assertEquals("Time zone ID is required", exception.getMessage());
    }

    @Test
    void applyTimeZone_rejectsInvalidZone() {
        School school = new School();

        InvalidSchoolTimeZoneException exception = assertThrows(
                InvalidSchoolTimeZoneException.class,
                () -> school.applyTimeZone("IST")
        );

        assertEquals(
                "Time zone ID must be a valid IANA time zone",
                exception.getMessage()
        );
    }

    @Test
    void applyTimeZone_rejectsOverlengthZone() {
        School school = new School();

        InvalidSchoolTimeZoneException exception = assertThrows(
                InvalidSchoolTimeZoneException.class,
                () -> school.applyTimeZone("A".repeat(65))
        );

        assertEquals(
                "Time zone ID cannot exceed 64 characters",
                exception.getMessage()
        );
    }
}
