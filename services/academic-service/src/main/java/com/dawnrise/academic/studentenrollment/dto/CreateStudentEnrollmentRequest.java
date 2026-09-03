package com.dawnrise.academic.studentenrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateStudentEnrollmentRequest(

        @NotNull(message = "Student user ID is required")
        @Positive(message = "Student user ID must be positive")
        Long studentUserId,

        @NotBlank(message = "Roll number is required")
        @Size(
                max = 30,
                message = "Roll number cannot exceed 30 characters"
        )
        String rollNumber,

        @NotNull(message = "Enrollment date is required")
        LocalDate enrolledOn

) {
}