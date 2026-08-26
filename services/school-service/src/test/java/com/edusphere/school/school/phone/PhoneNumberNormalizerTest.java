package com.edusphere.school.school.phone;

import com.edusphere.school.school.exception.FieldValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberNormalizerTest {

    private final PhoneNumberNormalizer normalizer =
            new PhoneNumberNormalizer();

    @Test
    void normalizeRequired_whenIndianE164NumberHasFormatting_returnsCanonicalE164() {
        assertEquals(
                "+919876543210",
                normalizer.normalizeRequired(
                        "phone",
                        "+91 98765-43210"
                )
        );
    }

    @Test
    void normalizeRequired_whenInternationalNumberHasFormatting_returnsCanonicalE164() {
        assertEquals(
                "+14155552671",
                normalizer.normalizeRequired(
                        "initialAuthority.phone",
                        "+1 (415) 555-2671"
                )
        );
    }

    @Test
    void normalizeRequired_whenNumberHasNoCountryCode_rejectsWithoutDefaultRegion() {
        FieldValidationException exception = assertThrows(
                FieldValidationException.class,
                () -> normalizer.normalizeRequired(
                        "phone",
                        "9876543210"
                )
        );

        assertEquals(
                "Phone number must be valid",
                exception.getValidationErrors().get("phone")
        );
    }

    @Test
    void normalizeRequired_whenMalformed_rejectsWithFieldSpecificError() {
        FieldValidationException exception = assertThrows(
                FieldValidationException.class,
                () -> normalizer.normalizeRequired(
                        "initialAuthority.phone",
                        "not-a-phone"
                )
        );

        assertEquals(
                "Phone number must be valid",
                exception.getValidationErrors()
                        .get("initialAuthority.phone")
        );
    }
}
