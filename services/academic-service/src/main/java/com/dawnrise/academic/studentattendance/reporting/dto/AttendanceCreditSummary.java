package com.dawnrise.academic.studentattendance.reporting.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AttendanceCreditSummary(
        BigDecimal earnedCredit,
        BigDecimal possibleCredit,
        BigDecimal attendancePercentage
) {

    private static final int CREDIT_SCALE = 2;
    private static final int PERCENTAGE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(100);

    public AttendanceCreditSummary {
        if (earnedCredit == null || possibleCredit == null) {
            throw new IllegalArgumentException(
                    "Attendance credits are required"
            );
        }

        if (earnedCredit.signum() < 0
                || possibleCredit.signum() < 0
                || earnedCredit.compareTo(possibleCredit) > 0) {
            throw new IllegalArgumentException(
                    "Attendance credits are invalid"
            );
        }

        earnedCredit = earnedCredit.setScale(
                CREDIT_SCALE,
                RoundingMode.HALF_UP
        );

        possibleCredit = possibleCredit.setScale(
                CREDIT_SCALE,
                RoundingMode.HALF_UP
        );

        attendancePercentage =
                calculatePercentage(
                        earnedCredit,
                        possibleCredit
                );
    }

    public static AttendanceCreditSummary from(
            BigDecimal earnedCredit,
            BigDecimal possibleCredit
    ) {
        return new AttendanceCreditSummary(
                earnedCredit,
                possibleCredit,
                BigDecimal.ZERO
        );
    }

    private static BigDecimal calculatePercentage(
            BigDecimal earnedCredit,
            BigDecimal possibleCredit
    ) {
        if (possibleCredit.signum() == 0) {
            return BigDecimal.ZERO.setScale(
                    PERCENTAGE_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return earnedCredit
                .multiply(ONE_HUNDRED)
                .divide(
                        possibleCredit,
                        PERCENTAGE_SCALE,
                        RoundingMode.HALF_UP
                );
    }
}