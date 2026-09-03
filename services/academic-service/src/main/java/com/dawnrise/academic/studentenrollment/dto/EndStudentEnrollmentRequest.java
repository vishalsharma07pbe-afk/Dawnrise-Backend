package com.dawnrise.academic.studentenrollment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record EndStudentEnrollmentRequest(

        @NotNull(message = "Enrollment end date is required")
        LocalDate endedOn,

        @NotNull(message = "Version is required")
        @PositiveOrZero(
                message = "Version must be greater than or equal to zero"
        )
        Long version

) {
}