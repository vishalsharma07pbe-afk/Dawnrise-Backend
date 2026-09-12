package com.dawnrise.academic.studentattendance.correction.service;

import com.dawnrise.academic.studentattendance.correction.dto.CreateStudentAttendanceCorrectionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionDecisionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionResponse;
import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudentAttendanceCorrectionService {

    StudentAttendanceCorrectionResponse create(
            long organizationId,
            long actorUserId,
            long attendanceSessionId,
            CreateStudentAttendanceCorrectionRequest request
    );

    StudentAttendanceCorrectionResponse get(long organizationId, long actorUserId, long requestId);

    Page<StudentAttendanceCorrectionResponse> list(
            long organizationId,
            long actorUserId,
            Long attendanceSessionId,
            StudentAttendanceCorrectionStatus status,
            Pageable pageable
    );

    StudentAttendanceCorrectionResponse cancel(
            long organizationId,
            long actorUserId,
            long requestId,
            StudentAttendanceCorrectionDecisionRequest request
    );

    StudentAttendanceCorrectionResponse reject(
            long organizationId,
            long actorUserId,
            long requestId,
            StudentAttendanceCorrectionDecisionRequest request
    );

    StudentAttendanceCorrectionResponse approve(
            long organizationId,
            long actorUserId,
            long requestId,
            StudentAttendanceCorrectionDecisionRequest request
    );
}
