package com.dawnrise.academic.academicyearrollover.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RolloverFingerprintServiceTest {

    private final RolloverFingerprintService service =
            new RolloverFingerprintService();

    @Test
    void requestHashIsStableForCanonicalFilterOrdering() {
        RolloverOptions first = new RolloverOptions(
                10L,
                20L,
                true,
                true,
                true,
                true,
                List.of(1L, 2L),
                List.of(3L, 4L)
        );
        RolloverOptions second = new RolloverOptions(
                10L,
                20L,
                true,
                true,
                true,
                true,
                List.of(1L, 2L),
                List.of(3L, 4L)
        );

        assertThat(service.requestHash(5L, first))
                .isEqualTo(service.requestHash(5L, second))
                .matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void requestHashChangesWhenMaterialIntentChanges() {
        RolloverOptions first = new RolloverOptions(
                10L,
                20L,
                true,
                false,
                true,
                false,
                List.of(),
                List.of()
        );
        RolloverOptions second = new RolloverOptions(
                10L,
                21L,
                true,
                false,
                true,
                false,
                List.of(),
                List.of()
        );

        assertThat(service.requestHash(5L, first))
                .isNotEqualTo(service.requestHash(5L, second));
    }
}
