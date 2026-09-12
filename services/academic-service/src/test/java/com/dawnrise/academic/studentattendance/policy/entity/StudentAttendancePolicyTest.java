package com.dawnrise.academic.studentattendance.policy.entity;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.exception.InvalidStudentAttendancePolicyException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendancePolicyTest {

    @Test
    void createsDailyPolicyWithPositiveActors() {
        StudentAttendancePolicy policy = new StudentAttendancePolicy(
                10L,
                AttendanceMode.DAILY,
                DayOfWeek.MONDAY,
                10,
                20,
                50L
        );

        assertThat(policy.getOrganizationId()).isEqualTo(10L);
        assertThat(policy.getUpdatedByUserId()).isEqualTo(50L);
    }

    @Test
    void rejectsInvalidSubmissionWindow() {
        assertThatThrownBy(() -> new StudentAttendancePolicy(
                10L,
                AttendanceMode.DAILY,
                DayOfWeek.MONDAY,
                20,
                20,
                50L
        )).isInstanceOf(InvalidStudentAttendancePolicyException.class);
    }

    @Test
    void rejectsUnmarkedStatusPolicy() {
        assertThatThrownBy(() -> new StudentAttendanceStatusPolicy(
                10L,
                AttendanceStatus.UNMARKED,
                BigDecimal.ZERO,
                BigDecimal.ONE
        )).isInstanceOf(InvalidStudentAttendancePolicyException.class);
    }

    @Test
    void rejectsEarnedCreditGreaterThanPossibleCredit() {
        assertThatThrownBy(() -> new StudentAttendanceStatusPolicy(
                10L,
                AttendanceStatus.HALF_DAY,
                new BigDecimal("0.75"),
                new BigDecimal("0.50")
        )).isInstanceOf(InvalidStudentAttendancePolicyException.class);
    }
}
