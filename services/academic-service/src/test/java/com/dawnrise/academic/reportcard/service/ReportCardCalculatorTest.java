package com.dawnrise.academic.reportcard.service;

import com.dawnrise.academic.grading.dto.GradeBandRequest;
import com.dawnrise.academic.grading.dto.GradingPolicyRequest;
import com.dawnrise.academic.grading.entity.GradingPolicy;
import com.dawnrise.academic.results.exception.InvalidResultException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportCardCalculatorTest {
    private static GradingPolicy policy(boolean absentFails, boolean every, RoundingMode rounding) {
        GradingPolicy policy = new GradingPolicy(1, 2, new GradingPolicyRequest(
                3, 4, "Policy", null, new BigDecimal("40.00"), new BigDecimal("40.00"),
                absentFails, every, 2, rounding,
                List.of(new GradeBandRequest(
                        "G", new BigDecimal("0.00"), new BigDecimal("100.00"), 1, null)),
                null));
        policy.activate(2, 1);
        return policy;
    }

    @Test
    void calculatesDeterministicRoundedTotals() {
        ReportCardCalculator.Result result = new ReportCardCalculator().calculate(
                policy(true, true, RoundingMode.HALF_UP),
                List.of(
                        new ReportCardCalculator.Source(
                                1, "M", "Math", new BigDecimal("30"), new BigDecimal("20"), false),
                        new ReportCardCalculator.Source(
                                2, "S", "Science", new BigDecimal("70"), new BigDecimal("40"), false)));

        assertEquals(new BigDecimal("60.00"), result.aggregate());
        assertEquals(new BigDecimal("66.67"), result.subjects().getFirst().percentage());
        assertTrue(result.passed());
    }

    @Test
    void absenceNeverInventsMarksAndCanAutomaticallyFailCard() {
        ReportCardCalculator.Result result = new ReportCardCalculator().calculate(
                policy(true, false, RoundingMode.HALF_UP),
                List.of(new ReportCardCalculator.Source(
                        1, "M", "Math", new BigDecimal("100"), null, true)));

        assertNull(result.subjects().getFirst().source().obtainedMarks());
        assertNull(result.subjects().getFirst().percentage());
        assertEquals(BigDecimal.ZERO, result.totalObtained());
        assertFalse(result.passed());
        assertEquals(1, result.absentCount());
    }

    @Test
    void absenceWithoutAutomaticFailureStillRemainsInAggregateDenominator() {
        ReportCardCalculator.Result result = new ReportCardCalculator().calculate(
                policy(false, false, RoundingMode.HALF_UP),
                List.of(
                        new ReportCardCalculator.Source(
                                1, "M", "Math", new BigDecimal("100"), null, true),
                        new ReportCardCalculator.Source(
                                2, "S", "Science", new BigDecimal("100"), new BigDecimal("100"), false)));

        assertEquals(new BigDecimal("200"), result.totalMaximum());
        assertEquals(new BigDecimal("100"), result.totalObtained());
        assertEquals(new BigDecimal("50.00"), result.aggregate());
        assertTrue(result.passed(), "absence does not automatically fail an otherwise passing card");
    }

    @Test
    void requireEverySubjectFailsOtherwisePassingAggregate() {
        ReportCardCalculator.Result result = new ReportCardCalculator().calculate(
                policy(false, true, RoundingMode.HALF_UP),
                List.of(
                        new ReportCardCalculator.Source(
                                1, "A", "A", new BigDecimal("100"), new BigDecimal("39"), false),
                        new ReportCardCalculator.Source(
                                2, "B", "B", new BigDecimal("100"), new BigDecimal("100"), false)));

        assertTrue(result.aggregate().compareTo(new BigDecimal("40")) > 0);
        assertFalse(result.passed());
    }

    @Test
    void rejectsZeroMaximumMarks() {
        assertThrows(InvalidResultException.class, () -> new ReportCardCalculator().calculate(
                policy(true, true, RoundingMode.HALF_UP),
                List.of(new ReportCardCalculator.Source(
                        1, "A", "A", BigDecimal.ZERO, BigDecimal.ZERO, false))));
    }
}
