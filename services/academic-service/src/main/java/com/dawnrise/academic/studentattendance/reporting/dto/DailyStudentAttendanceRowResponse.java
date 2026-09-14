package com.dawnrise.academic.studentattendance.reporting.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;

import java.math.BigDecimal;

public record DailyStudentAttendanceRowResponse(
        Long attendanceRecordId,
        Long studentEnrollmentId,
        Long studentUserId,
        String rollNumber,
        AttendanceStatus recordedStatus,
        AttendanceStatus effectiveStatus,
        BigDecimal earnedCredit,
        BigDecimal possibleCredit,
        boolean latePenaltyApplied,
        String remarks
) {

    public DailyStudentAttendanceRowResponse {
        if (attendanceRecordId == null
                || attendanceRecordId <= 0) {
            throw new IllegalArgumentException(
                    "Attendance record ID must be positive"
            );
        }

        if (studentEnrollmentId == null
                || studentEnrollmentId <= 0) {
            throw new IllegalArgumentException(
                    "Student enrollment ID must be positive"
            );
        }

        if (studentUserId == null
                || studentUserId <= 0) {
            throw new IllegalArgumentException(
                    "Student user ID must be positive"
            );
        }

        if (rollNumber == null || rollNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Student roll number is required"
            );
        }

        if (recordedStatus == null || effectiveStatus == null) {
            throw new IllegalArgumentException(
                    "Attendance statuses are required"
            );
        }

        if (earnedCredit == null || possibleCredit == null) {
            throw new IllegalArgumentException(
                    "Attendance credits are required"
            );
        }

        if (earnedCredit.signum() < 0
                || possibleCredit.signum() < 0
                || earnedCredit.compareTo(possibleCredit) > 0) {
            throw new IllegalArgumentException(
                    "Attendance credits are invalid"
            );
        }

        rollNumber = rollNumber.trim();
        remarks = normalizeOptionalText(remarks);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}