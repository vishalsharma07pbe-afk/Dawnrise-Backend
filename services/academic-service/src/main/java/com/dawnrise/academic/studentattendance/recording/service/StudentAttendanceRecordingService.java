package com.dawnrise.academic.studentattendance.recording.service;

import com.dawnrise.academic.studentattendance.recording.dto.BulkStudentAttendanceRecordRequest;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.recording.dto.SubmitStudentAttendanceRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncAppliedResult;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineDraftSnapshot;

import java.time.LocalDate;

public interface StudentAttendanceRecordingService {

    StudentAttendanceOfflineDraftSnapshot previewOfflineDraft(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate
    );

    StudentAttendanceOfflineSyncAppliedResult synchronizeOfflineDraft(
            long organizationId,
            long actorUserId,
            StudentAttendanceOfflineSyncRequest request
    );

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
            long sessionId,
            SubmitStudentAttendanceRequest request
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
