package com.dawnrise.academic.gradelevel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateGradeLevelRequest(

        @NotBlank(message = "Grade level code is required")
        @Size(
                max = 50,
                message = "Grade level code cannot exceed 50 characters"
        )
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "Grade level code can contain only letters, numbers, hyphens, and underscores"
        )
        String code,

        @NotBlank(message = "Grade level name is required")
        @Size(
                max = 100,
                message = "Grade level name cannot exceed 100 characters"
        )
        String name,

        @NotNull(message = "Display order is required")
        @Positive(message = "Display order must be greater than zero")
        Integer displayOrder

) {
}