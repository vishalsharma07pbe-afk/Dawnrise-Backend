package com.dawnrise.identity.common.phone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberNormalizerTest {

    private final PhoneNumberNormalizer normalizer =
            new PhoneNumberNormalizer();

    @Test
    void normalizeOptional_whenPresent_returnsCanonicalE164() {
        assertEquals(
                "+442071838750",
                normalizer.normalizeOptional("+44 20 7183 8750")
        );
    }

    @Test
    void normalizeOptional_whenBlank_preservesBlank() {
        assertEquals("", normalizer.normalizeOptional(""));
    }

    @Test
    void normalizeOptional_whenInvalid_rejectsWithoutRawDetails() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalizeOptional("020 7183 8750")
        );

        assertEquals(
                "Authority phone number must be valid",
                exception.getMessage()
        );
    }
}
