package com.dawnrise.academic.grading.entity;

import com.dawnrise.academic.grading.dto.GradeBandRequest;
import com.dawnrise.academic.grading.dto.GradingPolicyRequest;
import com.dawnrise.academic.results.exception.InvalidResultException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradingPolicyValidationTest {
    @Test
    void activationAcceptsCoverageAtEverySupportedRepresentativeScale() {
        for (int scale : List.of(0, 2, 4, 6)) {
            BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
            BigDecimal boundary = new BigDecimal("40").setScale(scale);
            GradingPolicy policy = policy(scale, RoundingMode.HALF_UP, List.of(
                    band("Fail", BigDecimal.ZERO.setScale(scale), boundary.subtract(unit), 1),
                    band("Pass", boundary, new BigDecimal("100").setScale(scale), 2)));

            assertDoesNotThrow(() -> policy.activate(9, 1));
            assertEquals(1, policy.getPolicyVersion());
        }
    }

    @Test
    void activationIgnoresDatabaseTrailingZeroScale() {
        GradingPolicy policy = policy(2, RoundingMode.HALF_UP, List.of(
                band("Fail", new BigDecimal("0.000000"), new BigDecimal("39.990000"), 1),
                band("Pass", new BigDecimal("40.000000"), new BigDecimal("100.000000"), 2)));

        assertDoesNotThrow(() -> policy.activate(9, 1));
        assertEquals(1, policy.getPolicyVersion());
    }

    @Test
    void rejectsSharedInclusiveBoundary() {
        assertThrows(InvalidResultException.class, () -> policy(2, RoundingMode.HALF_UP, List.of(
                band("Fail", new BigDecimal("0.00"), new BigDecimal("40.00"), 1),
                band("Pass", new BigDecimal("40.00"), new BigDecimal("100.00"), 2))));
    }

    @Test
    void activationRejectsGapAtConfiguredPrecision() {
        GradingPolicy policy = policy(2, RoundingMode.HALF_UP, List.of(
                band("Fail", new BigDecimal("0.00"), new BigDecimal("39.98"), 1),
                band("Pass", new BigDecimal("40.00"), new BigDecimal("100.00"), 2)));

        assertThrows(InvalidResultException.class, () -> policy.activate(9, 1));
    }

    @Test
    void rejectsBoundaryScaleBeyondConfiguredScaleWithoutRounding() {
        assertThrows(InvalidResultException.class, () -> policy(2, RoundingMode.HALF_UP, List.of(
                band("Fail", new BigDecimal("0.000"), new BigDecimal("39.999"), 1),
                band("Pass", new BigDecimal("40.000"), new BigDecimal("100.000"), 2))));
    }

    @Test
    void rejectsUnnecessaryRoundingMode() {
        assertThrows(InvalidResultException.class, () -> policy(
                2, RoundingMode.UNNECESSARY,
                List.of(band("All", new BigDecimal("0.00"), new BigDecimal("100.00"), 1))));
    }

    private static GradingPolicy policy(
            int scale,
            RoundingMode roundingMode,
            List<GradeBandRequest> bands
    ) {
        return new GradingPolicy(1, 9, new GradingPolicyRequest(
                2, 3, "Policy", null, new BigDecimal("40").setScale(scale),
                new BigDecimal("40").setScale(scale), true, true, scale,
                roundingMode, bands, null));
    }

    private static GradeBandRequest band(
            String label,
            BigDecimal minimum,
            BigDecimal maximum,
            int order
    ) {
        return new GradeBandRequest(label, minimum, maximum, order, null);
    }
}
