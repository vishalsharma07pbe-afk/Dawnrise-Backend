package com.dawnrise.academic.teacherassignment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateTeacherAssignmentRequest(

        @NotNull(message = "Teacher user ID is required")
        @Positive(message = "Teacher user ID must be positive")
        Long teacherUserId,

        @NotNull(message = "Version is required")
        @PositiveOrZero(
                message = "Version must be greater than or equal to zero"
        )
        Long version

) {
}