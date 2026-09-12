package com.dawnrise.academic.studentattendance.correction.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StudentAttendanceCorrectionItemResponse(
        Long id,
        Long attendanceRecordId,
        Long studentEnrollmentId,
        Long studentUserId,
        Long expectedAttendanceRecordVersion,
        AttendanceStatus previousRecordedStatus,
        AttendanceStatus previousEffectiveStatus,
        BigDecimal previousEarnedCredit,
        BigDecimal previousPossibleCredit,
        boolean previousLatePenaltyApplied,
        String previousRemarks,
        AttendanceStatus proposedRecordedStatus,
        AttendanceStatus proposedEffectiveStatus,
        BigDecimal proposedEarnedCredit,
        BigDecimal proposedPossibleCredit,
        boolean proposedLatePenaltyApplied,
        String proposedRemarks,
        OffsetDateTime createdAt
) {
}
