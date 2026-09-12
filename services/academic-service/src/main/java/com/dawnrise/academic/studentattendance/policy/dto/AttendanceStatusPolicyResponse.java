package com.dawnrise.academic.studentattendance.policy.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;

import java.math.BigDecimal;

public record AttendanceStatusPolicyResponse(
        AttendanceStatus attendanceStatus,
        BigDecimal earnedCredit,
        BigDecimal possibleCredit,
        Long version
) {
}
