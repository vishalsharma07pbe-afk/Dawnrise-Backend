package com.dawnrise.academic.studentattendance.recording.dto;

import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record StudentAttendanceSessionResponse(
        Long id,
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        Long academicCalendarDayId,
        LocalDate attendanceDate,
        StudentAttendanceSessionStatus lifecycleStatus,
        StudentAttendanceSubmissionType submissionType,
        Long createdByUserId,
        Long updatedByUserId,
        Long submittedByUserId,
        OffsetDateTime submittedAt,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<StudentAttendanceRecordResponse> records
) {
}
