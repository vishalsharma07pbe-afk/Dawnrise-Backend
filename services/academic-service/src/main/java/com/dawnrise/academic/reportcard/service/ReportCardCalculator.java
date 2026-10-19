package com.dawnrise.academic.reportcard.service;

import com.dawnrise.academic.grading.entity.GradingPolicy;
import com.dawnrise.academic.grading.dto.GradeBandRequest;
import com.dawnrise.academic.results.exception.InvalidResultException;

import java.math.*;
import java.util.*;

public final class ReportCardCalculator {
    public record Source(long subjectId, String code, String name, BigDecimal maximumMarks, BigDecimal obtainedMarks,
                         boolean absent) {
    }

    public record CalculatedSubject(Source source, BigDecimal percentage, String grade, boolean passed, int order) {
    }

    public record Result(List<CalculatedSubject> subjects, BigDecimal totalMaximum, BigDecimal totalObtained,
                         BigDecimal aggregate, String overallGrade, boolean passed, int absentCount) {
    }

    public Result calculate(GradingPolicy policy, List<Source> sources) {
        if (sources.isEmpty()) throw new InvalidResultException("Published result contains no marks");
        BigDecimal max = BigDecimal.ZERO, obtained = BigDecimal.ZERO;
        int absent = 0, order = 0;
        boolean every = true;
        List<CalculatedSubject> rows = new ArrayList<>();
        for (Source s : sources) {
            if (s.maximumMarks() == null || s.maximumMarks().signum() <= 0)
                throw new InvalidResultException("Maximum marks must be greater than zero");
            max = max.add(s.maximumMarks());
            BigDecimal pct = null;
            boolean pass;
            if (s.absent()) {
                absent++;
                pass = !policy.getAbsentCountsAsFailure();
            } else {
                if (s.obtainedMarks() == null) throw new InvalidResultException("Published mark is incomplete");
                obtained = obtained.add(s.obtainedMarks());
                pct = percentage(s.obtainedMarks(), s.maximumMarks(), policy);
                pass = pct.compareTo(policy.getSubjectPassPercentage()) >= 0;
            }
            every &= pass;
            rows.add(new CalculatedSubject(s, pct, pct == null ? null : band(policy, pct), pass, ++order));
        }
        BigDecimal aggregate = percentage(obtained, max, policy);
        boolean pass = aggregate.compareTo(policy.getOverallPassPercentage()) >= 0 && (!policy.getRequireEverySubjectToPass() || every) && (!policy.getAbsentCountsAsFailure() || absent == 0);
        return new Result(List.copyOf(rows), max, obtained, aggregate, band(policy, aggregate), pass, absent);
    }

    private static BigDecimal percentage(BigDecimal value, BigDecimal max, GradingPolicy p) {
        return value.multiply(new BigDecimal("100")).divide(max, p.getDecimalScale(), p.getRoundingMode());
    }

    private static String band(GradingPolicy p, BigDecimal pct) {
        return p.bandResponses().stream().filter(b -> pct.compareTo(b.minimumPercentage()) >= 0 && pct.compareTo(b.maximumPercentage()) <= 0).sorted(Comparator.comparingInt(GradeBandRequest::displayOrder)).map(GradeBandRequest::label).findFirst().orElseThrow(() -> new InvalidResultException("No grade band covers percentage " + pct));
    }
}
