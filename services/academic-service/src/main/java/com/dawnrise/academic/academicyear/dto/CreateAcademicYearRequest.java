package com.dawnrise.academic.academicyear.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateAcademicYearRequest(

        @NotBlank(message = "Academic year name is required")
        @Size(
                max = 50,
                message = "Academic year name cannot exceed 50 characters"
        )
        String name,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate

) {
}