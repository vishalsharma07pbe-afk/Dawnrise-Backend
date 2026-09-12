package com.dawnrise.academic.studentattendance.policy.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AttendanceStatusPolicyRequest(
        @NotNull AttendanceStatus attendanceStatus,
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal earnedCredit,
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal possibleCredit
) {
}
