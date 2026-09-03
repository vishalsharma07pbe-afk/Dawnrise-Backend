package com.dawnrise.academic.teacherassignment.dto;

import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;

import java.time.OffsetDateTime;

public record TeacherAssignmentResponse(
        Long id,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        Long gradeLevelSubjectId,
        Long teacherUserId,
        TeacherAssignmentType assignmentType,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}