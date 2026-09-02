package com.dawnrise.academic.section.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateSectionRequest(

        @NotBlank(message = "Section code is required")
        @Size(
                max = 50,
                message = "Section code cannot exceed 50 characters"
        )
        String code,

        @NotBlank(message = "Section name is required")
        @Size(
                max = 100,
                message = "Section name cannot exceed 100 characters"
        )
        String name,

        @NotNull(message = "Display order is required")
        @Positive(message = "Display order must be greater than zero")
        Integer displayOrder,

        @NotNull(message = "Version is required")
        @PositiveOrZero(
                message = "Version must be greater than or equal to zero"
        )
        Long version

) {
}