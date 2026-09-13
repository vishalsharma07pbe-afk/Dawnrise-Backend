package com.dawnrise.academic.studentattendance.offlinesync.dto;

public record StudentAttendanceOfflineDraftRosterEntry(
        Long studentEnrollmentId,
        String rollNumber,
        Long existingRecordVersion
) {
}
