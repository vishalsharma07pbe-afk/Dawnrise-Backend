package com.dawnrise.academic.studentenrollment.dto;

import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record StudentEnrollmentResponse(
        Long id,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        Long studentUserId,
        String rollNumber,
        StudentEnrollmentStatus status,
        LocalDate enrolledOn,
        LocalDate endedOn,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}