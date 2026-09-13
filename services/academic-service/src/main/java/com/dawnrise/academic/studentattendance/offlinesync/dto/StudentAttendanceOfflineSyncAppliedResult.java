package com.dawnrise.academic.studentattendance.offlinesync.dto;

import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceRecordResponse;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;

import java.util.List;

public record StudentAttendanceOfflineSyncAppliedResult(
        StudentAttendanceSessionResponse session,
        List<StudentAttendanceRecordResponse> appliedRecords
) {
}

