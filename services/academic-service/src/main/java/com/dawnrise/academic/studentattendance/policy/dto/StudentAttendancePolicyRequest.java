package com.dawnrise.academic.studentattendance.policy.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.util.List;

public record StudentAttendancePolicyRequest(
        @NotNull AttendanceMode attendanceMode,
        @NotNull DayOfWeek weekStartDay,
        @NotNull @Min(1) @Max(240) Integer draftWarningMinutes,
        @NotNull @Min(2) @Max(480) Integer automaticSubmissionMinutes,
        @NotNull @Min(0) Long expectedVersion,
        @NotEmpty List<@Valid AttendanceStatusPolicyRequest> statusPolicies
) {
}
