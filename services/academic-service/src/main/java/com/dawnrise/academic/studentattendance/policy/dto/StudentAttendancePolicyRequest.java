package com.dawnrise.academic.studentattendance.policy.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.LateCountingPeriod;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;
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
        @NotNull Boolean latePenaltyEnabled,
        @NotNull @Min(1) @Max(100) Integer lateOccurrencesThreshold,
        @NotNull LatePenaltyOutcome latePenaltyOutcome,
        @NotNull LateCountingPeriod lateCountingPeriod,
        @NotNull @Min(0) Long expectedVersion,
        @NotEmpty List<@Valid AttendanceStatusPolicyRequest> statusPolicies
) {
}
