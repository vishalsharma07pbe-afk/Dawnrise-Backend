package com.dawnrise.academic.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(

        @NotBlank(message = "Subject code is required")
        @Size(
                max = 50,
                message = "Subject code cannot exceed 50 characters"
        )
        String code,

        @NotBlank(message = "Subject name is required")
        @Size(
                max = 120,
                message = "Subject name cannot exceed 120 characters"
        )
        String name,

        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "Subject description cannot be blank"
        )
        @Size(
                max = 500,
                message = "Subject description cannot exceed 500 characters"
        )
        String description,

        @NotNull(message = "Version is required")
        @PositiveOrZero(
                message = "Version must be greater than or equal to zero"
        )
        Long version

) {
}