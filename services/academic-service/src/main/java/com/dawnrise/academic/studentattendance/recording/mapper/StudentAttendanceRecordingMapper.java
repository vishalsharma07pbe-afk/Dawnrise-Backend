package com.dawnrise.academic.studentattendance.recording.mapper;

import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceRecordResponse;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StudentAttendanceRecordingMapper {

    public StudentAttendanceSessionResponse toResponse(
            StudentAttendanceSession session,
            List<StudentAttendanceRecord> records
    ) {
        return new StudentAttendanceSessionResponse(
                session.getId(),
                session.getOrganizationId(),
                session.getAcademicYearId(),
                session.getGradeLevelId(),
                session.getSectionId(),
                session.getAcademicCalendarDayId(),
                session.getAttendanceDate(),
                session.getLifecycleStatus(),
                session.getSubmissionType(),
                session.getCreatedByUserId(),
                session.getUpdatedByUserId(),
                session.getSubmittedByUserId(),
                session.getSubmittedAt(),
                session.getVersion(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                records.stream().map(this::toResponse).toList()
        );
    }

    public StudentAttendanceRecordResponse toResponse(StudentAttendanceRecord record) {
        return new StudentAttendanceRecordResponse(
                record.getId(),
                record.getStudentEnrollmentId(),
                record.getStudentUserId(),
                record.getRecordedStatus(),
                record.getEffectiveStatus(),
                record.getEarnedCredit(),
                record.getPossibleCredit(),
                record.isLatePenaltyApplied(),
                record.getRemarks(),
                record.getMarkedByUserId(),
                record.getUpdatedByUserId(),
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
