package com.dawnrise.academic.studentattendance.recording.service;

import com.dawnrise.academic.studentattendance.recording.dto.BulkStudentAttendanceRecordRequest;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;

import java.time.LocalDate;

public interface StudentAttendanceRecordingService {

    StudentAttendanceSessionResponse getOrCreateDraft(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate
    );

    StudentAttendanceSessionResponse getSession(
            long organizationId,
            long actorUserId,
            long sessionId
    );

    StudentAttendanceSessionResponse saveDraftRecords(
            long organizationId,
            long actorUserId,
            long sessionId,
            BulkStudentAttendanceRecordRequest request
    );

    StudentAttendanceSessionResponse submitManually(
            long organizationId,
            long actorUserId,
            long sessionId
    );

    StudentAttendanceSessionResponse getSectionAttendanceForDate(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate
    );
}
