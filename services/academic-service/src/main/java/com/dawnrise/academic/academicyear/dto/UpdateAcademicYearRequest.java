package com.dawnrise.academic.academicyear.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateAcademicYearRequest(

        @NotBlank(message = "Academic year name is required")
        @Size(
                max = 50,
                message = "Academic year name cannot exceed 50 characters"
        )
        String name,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @NotNull(message = "Version is required")
        @PositiveOrZero(message = "Version cannot be negative")
        Long version

) {
}