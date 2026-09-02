package com.dawnrise.academic.section.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateSectionsRequest(

        @NotEmpty(message = "At least one section is required")
        @Size(
                max = 20,
                message = "A maximum of 20 sections can be created at once"
        )
        List<@Valid CreateSectionRequest> sections

) {
}
