package com.dawnrise.academic.section.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApplySectionStructureRequest(

        @NotEmpty(message = "At least one grade level is required")
        @Size(
                max = 50,
                message = "A maximum of 50 grade levels can be selected at once"
        )
        List<
                @NotNull(message = "Grade level ID is required")
                @Positive(message = "Grade level ID must be positive")
                        Long
                > gradeLevelIds,

        @NotEmpty(message = "At least one section is required")
        @Size(
                max = 20,
                message = "A maximum of 20 sections can be applied to each grade"
        )
        List<@Valid CreateSectionRequest> sections

) {
}