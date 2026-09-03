package com.dawnrise.academic.gradelevelsubject.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateGradeLevelSubjectRequest(

        @NotNull(message = "Mandatory value is required")
        Boolean mandatory,

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