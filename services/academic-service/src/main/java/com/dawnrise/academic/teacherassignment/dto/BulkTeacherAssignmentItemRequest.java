package com.dawnrise.academic.teacherassignment.dto;

import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BulkTeacherAssignmentItemRequest(

        @NotNull(message = "Grade level ID is required")
        @Positive(message = "Grade level ID must be positive")
        Long gradeLevelId,

        @NotNull(message = "Section ID is required")
        @Positive(message = "Section ID must be positive")
        Long sectionId,

        @Positive(message = "Grade subject ID must be positive")
        Long gradeLevelSubjectId,

        @NotNull(message = "Teacher user ID is required")
        @Positive(message = "Teacher user ID must be positive")
        Long teacherUserId,

        @NotNull(message = "Assignment type is required")
        TeacherAssignmentType assignmentType

) {
}
