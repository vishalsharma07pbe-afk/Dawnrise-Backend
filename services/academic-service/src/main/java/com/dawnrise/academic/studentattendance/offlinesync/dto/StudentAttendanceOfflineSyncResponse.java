package com.dawnrise.academic.studentattendance.offlinesync.dto;

import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceRecordResponse;

import java.util.List;

public record StudentAttendanceOfflineSyncResponse(
        Long operationId,
        String idempotencyKey,
        String requestHash,
        boolean replay,
        StudentAttendanceSessionResponse session,
        List<StudentAttendanceRecordResponse> appliedRecords
) {
    public StudentAttendanceOfflineSyncResponse asReplay() {
        return new StudentAttendanceOfflineSyncResponse(
                operationId,
                idempotencyKey,
                requestHash,
                true,
                session,
                appliedRecords
        );
    }
}
