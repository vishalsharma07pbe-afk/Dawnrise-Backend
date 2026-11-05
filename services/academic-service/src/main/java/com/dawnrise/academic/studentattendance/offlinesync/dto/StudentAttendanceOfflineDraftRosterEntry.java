package com.dawnrise.academic.studentattendance.offlinesync.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;

public record StudentAttendanceOfflineDraftRosterEntry(
        Long studentEnrollmentId,
        Long studentUserId,
        String rollNumber,
        Long existingRecordVersion,
        AttendanceStatus approvedLeaveStatus
) {
    public StudentAttendanceOfflineDraftRosterEntry(Long studentEnrollmentId, Long studentUserId,
                                                    String rollNumber, Long existingRecordVersion) {
        this(studentEnrollmentId, studentUserId, rollNumber, existingRecordVersion, null);
    }
}
