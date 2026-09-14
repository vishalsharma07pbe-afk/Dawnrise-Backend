package com.dawnrise.academic.studentattendance.notificationoutbox.service;

import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionItem;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface StudentAttendanceNotificationOutboxService {

    void createForSubmittedSession(
            StudentAttendanceSession session,
            List<StudentAttendanceRecord> records
    );

    void createForApprovedCorrection(
            StudentAttendanceSession session,
            Long correctionRequestId,
            List<StudentAttendanceCorrectionItem> correctionItems,
            Map<Long, StudentAttendanceRecord> correctedRecords,
            OffsetDateTime occurredAt
    );
}