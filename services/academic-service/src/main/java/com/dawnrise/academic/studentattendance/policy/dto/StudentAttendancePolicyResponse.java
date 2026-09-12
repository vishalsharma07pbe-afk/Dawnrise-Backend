package com.dawnrise.academic.studentattendance.policy.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.List;

public record StudentAttendancePolicyResponse(
        Long organizationId,
        AttendanceMode attendanceMode,
        DayOfWeek weekStartDay,
        Integer draftWarningMinutes,
        Integer automaticSubmissionMinutes,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<AttendanceStatusPolicyResponse> statusPolicies
) {
}
