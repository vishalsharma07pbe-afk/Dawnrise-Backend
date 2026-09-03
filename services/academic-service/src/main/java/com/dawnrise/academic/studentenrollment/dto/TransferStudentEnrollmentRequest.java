package com.dawnrise.academic.studentenrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TransferStudentEnrollmentRequest(

        @NotNull(message = "Destination grade-level ID is required")
        @Positive(message = "Destination grade-level ID must be positive")
        Long destinationGradeLevelId,

        @NotNull(message = "Destination section ID is required")
        @Positive(message = "Destination section ID must be positive")
        Long destinationSectionId,

        @NotBlank(message = "New roll number is required")
        @Size(
                max = 30,
                message = "Roll number cannot exceed 30 characters"
        )
        String newRollNumber,

        @NotNull(message = "Transfer date is required")
        LocalDate transferOn,

        @NotNull(message = "Version is required")
        @PositiveOrZero(
                message = "Version must be greater than or equal to zero"
        )
        Long version

) {
}