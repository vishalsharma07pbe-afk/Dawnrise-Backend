package com.dawnrise.academic.teacherassignment.dto;

import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTeacherAssignmentRequest(

        @Positive(message = "Grade subject ID must be positive")
        Long gradeLevelSubjectId,

        @NotNull(message = "Teacher user ID is required")
        @Positive(message = "Teacher user ID must be positive")
        Long teacherUserId,

        @NotNull(message = "Assignment type is required")
        TeacherAssignmentType assignmentType

) {
}