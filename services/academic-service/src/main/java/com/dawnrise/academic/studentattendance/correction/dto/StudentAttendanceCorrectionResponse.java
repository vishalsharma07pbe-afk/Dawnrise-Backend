package com.dawnrise.academic.studentattendance.correction.dto;

import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record StudentAttendanceCorrectionResponse(
        Long id,
        Long organizationId,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        Long attendanceSessionId,
        String reason,
        StudentAttendanceCorrectionStatus status,
        Long requestedByUserId,
        Long reviewedByUserId,
        String reviewComment,
        OffsetDateTime requestedAt,
        OffsetDateTime reviewedAt,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<StudentAttendanceCorrectionItemResponse> items
) {
}
