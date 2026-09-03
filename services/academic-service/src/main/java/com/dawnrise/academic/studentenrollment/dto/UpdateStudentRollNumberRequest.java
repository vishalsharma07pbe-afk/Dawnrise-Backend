package com.dawnrise.academic.studentenrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateStudentRollNumberRequest(

        @NotBlank(message = "Roll number is required")
        @Size(
                max = 30,
                message = "Roll number cannot exceed 30 characters"
        )
        String rollNumber,

        @NotNull(message = "Version is required")
        @PositiveOrZero(
                message = "Version must be greater than or equal to zero"
        )
        Long version

) {
}