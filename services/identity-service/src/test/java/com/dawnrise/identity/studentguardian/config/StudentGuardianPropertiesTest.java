package com.dawnrise.identity.studentguardian.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentGuardianPropertiesTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validation_whenWithinRange_passes() {
        StudentGuardianProperties properties =
                new StudentGuardianProperties();
        properties.setMaxActiveGuardiansPerStudent(3);

        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void validation_whenBelowMinimum_fails() {
        StudentGuardianProperties properties =
                new StudentGuardianProperties();
        properties.setMaxActiveGuardiansPerStudent(0);

        assertEquals(1, validator.validate(properties).size());
    }

    @Test
    void validation_whenAboveMaximum_fails() {
        StudentGuardianProperties properties =
                new StudentGuardianProperties();
        properties.setMaxActiveGuardiansPerStudent(11);

        assertEquals(1, validator.validate(properties).size());
    }
}
