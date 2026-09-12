package com.dawnrise.academic.studentattendance.recording.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StudentAttendanceRecordResponse(
        Long id,
        Long studentEnrollmentId,
        Long studentUserId,
        AttendanceStatus recordedStatus,
        AttendanceStatus effectiveStatus,
        BigDecimal earnedCredit,
        BigDecimal possibleCredit,
        boolean latePenaltyApplied,
        String remarks,
        Long markedByUserId,
        Long updatedByUserId,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
