package com.dawnrise.academic.academicyear.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VoidAcademicYearRequest(

        @NotBlank(message = "Void reason is required")
        @Size(
                max = 500,
                message = "Void reason cannot exceed 500 characters"
        )
        String reason,

        @NotNull(message = "Version is required")
        @PositiveOrZero(message = "Version must be greater than or equal to zero")
        Long version

) {
}