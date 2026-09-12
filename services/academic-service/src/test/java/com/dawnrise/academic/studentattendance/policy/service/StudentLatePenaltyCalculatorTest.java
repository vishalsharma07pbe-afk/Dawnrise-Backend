package com.dawnrise.academic.studentattendance.policy.service;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;
import com.dawnrise.academic.studentattendance.policy.exception.InvalidStudentAttendancePolicyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentLatePenaltyCalculatorTest {

    private final StudentLatePenaltyCalculator calculator =
            new StudentLatePenaltyCalculator();

    @Test
    void nonLateStatusesRemainUnchanged() {
        assertThat(effective(AttendanceStatus.PRESENT, 3, true, 3))
                .isEqualTo(AttendanceStatus.PRESENT);
        assertThat(effective(AttendanceStatus.ABSENT, 3, true, 3))
                .isEqualTo(AttendanceStatus.ABSENT);
        assertThat(effective(AttendanceStatus.HALF_DAY, 3, true, 3))
                .isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(effective(AttendanceStatus.EXCUSED, 3, true, 3))
                .isEqualTo(AttendanceStatus.EXCUSED);
    }

    @Test
    void disabledPenaltyLeavesLateUnchanged() {
        assertThat(effective(AttendanceStatus.LATE, 3, false, 3))
                .isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void thresholdThreeOccurrencesOneTwoAndFourRemainLate() {
        assertThat(effective(AttendanceStatus.LATE, 1, true, 3))
                .isEqualTo(AttendanceStatus.LATE);
        assertThat(effective(AttendanceStatus.LATE, 2, true, 3))
                .isEqualTo(AttendanceStatus.LATE);
        assertThat(effective(AttendanceStatus.LATE, 4, true, 3))
                .isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void thresholdThreeOccurrenceThreeAndSixConvertToHalfDay() {
        assertThat(effective(AttendanceStatus.LATE, 3, true, 3))
                .isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(effective(AttendanceStatus.LATE, 6, true, 3))
                .isEqualTo(AttendanceStatus.HALF_DAY);
    }

    @Test
    void absentOutcomeConvertsThresholdOccurrencesToAbsent() {
        assertThat(calculator.effectiveStatus(
                AttendanceStatus.LATE,
                3,
                true,
                3,
                LatePenaltyOutcome.ABSENT
        )).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThatThrownBy(() -> effective(null, 1, true, 3))
                .isInstanceOf(InvalidStudentAttendancePolicyException.class);
        assertThatThrownBy(() -> calculator.effectiveStatus(
                AttendanceStatus.LATE,
                1,
                true,
                3,
                null
        )).isInstanceOf(InvalidStudentAttendancePolicyException.class);
        assertThatThrownBy(() -> effective(AttendanceStatus.LATE, 0, true, 3))
                .isInstanceOf(InvalidStudentAttendancePolicyException.class);
        assertThatThrownBy(() -> effective(AttendanceStatus.LATE, 1, true, 0))
                .isInstanceOf(InvalidStudentAttendancePolicyException.class);
        assertThatThrownBy(() -> effective(AttendanceStatus.LATE, 1, true, 101))
                .isInstanceOf(InvalidStudentAttendancePolicyException.class);
    }

    @Test
    void thresholdBoundariesOneAndOneHundredWork() {
        assertThat(effective(AttendanceStatus.LATE, 1, true, 1))
                .isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(effective(AttendanceStatus.LATE, 99, true, 100))
                .isEqualTo(AttendanceStatus.LATE);
        assertThat(effective(AttendanceStatus.LATE, 100, true, 100))
                .isEqualTo(AttendanceStatus.HALF_DAY);
    }

    private AttendanceStatus effective(
            AttendanceStatus status,
            int occurrence,
            boolean enabled,
            int threshold
    ) {
        return calculator.effectiveStatus(
                status,
                occurrence,
                enabled,
                threshold,
                LatePenaltyOutcome.HALF_DAY
        );
    }
}
