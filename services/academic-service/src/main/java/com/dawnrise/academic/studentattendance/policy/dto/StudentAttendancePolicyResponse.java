package com.dawnrise.academic.studentattendance.policy.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.LateCountingPeriod;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.List;

public record StudentAttendancePolicyResponse(
        Long organizationId,
        AttendanceMode attendanceMode,
        DayOfWeek weekStartDay,
        Integer draftWarningMinutes,
        Integer automaticSubmissionMinutes,
        Boolean latePenaltyEnabled,
        Integer lateOccurrencesThreshold,
        LatePenaltyOutcome latePenaltyOutcome,
        LateCountingPeriod lateCountingPeriod,
        Boolean deferredEntryEnabled,
        Integer teacherBackEntryDays,
        Integer leadershipBackEntryDays,
        Boolean automaticSubmissionEnabled,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<AttendanceStatusPolicyResponse> statusPolicies
) {
    public StudentAttendancePolicyResponse(
            Long organizationId,
            AttendanceMode attendanceMode,
            DayOfWeek weekStartDay,
            Integer draftWarningMinutes,
            Integer automaticSubmissionMinutes,
            Boolean latePenaltyEnabled,
            Integer lateOccurrencesThreshold,
            LatePenaltyOutcome latePenaltyOutcome,
            LateCountingPeriod lateCountingPeriod,
            Long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<AttendanceStatusPolicyResponse> statusPolicies
    ) {
        this(
                organizationId,
                attendanceMode,
                weekStartDay,
                draftWarningMinutes,
                automaticSubmissionMinutes,
                latePenaltyEnabled,
                lateOccurrencesThreshold,
                latePenaltyOutcome,
                lateCountingPeriod,
                true,
                0,
                30,
                false,
                version,
                createdAt,
                updatedAt,
                statusPolicies
        );
    }
}
