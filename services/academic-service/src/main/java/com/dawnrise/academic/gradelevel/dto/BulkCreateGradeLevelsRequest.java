package com.dawnrise.academic.gradelevel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateGradeLevelsRequest(

        @NotEmpty(message = "At least one grade level is required")
        @Size(
                max = 50,
                message = "A maximum of 50 grade levels can be created at once"
        )
        List<@Valid CreateGradeLevelRequest> gradeLevels

) {
}