package com.dawnrise.academic.gradelevelsubject.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApplyGradeLevelSubjectStructureRequest(
        @NotEmpty(message = "At least one grade level is required")
        @Size(max = 100, message = "A maximum of 100 grade levels can be selected at once")
        List<
                @NotNull(message = "Grade level ID is required")
                @Positive(message = "Grade level ID must be positive")
                        Long
                > gradeLevelIds,

        @NotEmpty(message = "At least one subject assignment is required")
        @Size(max = 100, message = "A maximum of 100 subject assignments can be applied to each grade")
        List<@Valid CreateGradeLevelSubjectRequest> subjects
) {
}
