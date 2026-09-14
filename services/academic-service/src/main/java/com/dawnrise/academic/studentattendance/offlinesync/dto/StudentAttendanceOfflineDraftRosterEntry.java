package com.dawnrise.academic.studentattendance.offlinesync.dto;

public record StudentAttendanceOfflineDraftRosterEntry(
        Long studentEnrollmentId,
        Long studentUserId,
        String rollNumber,
        Long existingRecordVersion
) {
}
