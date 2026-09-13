package com.dawnrise.academic.studentattendance.offlinesync.service;

import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;

public interface StudentAttendanceOfflineSyncService {
    StudentAttendanceOfflineSyncResponse synchronize(
            long organizationId,
            long actorUserId,
            String idempotencyKey,
            StudentAttendanceOfflineSyncRequest request
    );
}

