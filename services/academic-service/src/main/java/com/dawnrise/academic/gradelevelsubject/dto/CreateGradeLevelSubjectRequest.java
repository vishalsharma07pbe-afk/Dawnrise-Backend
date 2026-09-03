package com.dawnrise.academic.gradelevelsubject.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateGradeLevelSubjectRequest(

        @NotNull(message = "Subject ID is required")
        @Positive(message = "Subject ID must be positive")
        Long subjectId,

        @NotNull(message = "Mandatory value is required")
        Boolean mandatory,

        @NotNull(message = "Display order is required")
        @Positive(message = "Display order must be greater than zero")
        Integer displayOrder

) {
}