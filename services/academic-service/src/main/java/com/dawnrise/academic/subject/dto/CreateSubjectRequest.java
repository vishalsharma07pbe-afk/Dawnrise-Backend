package com.dawnrise.academic.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(

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
        String description

) {
}