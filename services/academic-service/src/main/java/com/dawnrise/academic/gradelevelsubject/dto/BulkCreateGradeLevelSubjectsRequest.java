package com.dawnrise.academic.gradelevelsubject.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateGradeLevelSubjectsRequest(
        @NotEmpty(message = "At least one subject assignment is required")
        @Size(max = 100, message = "A maximum of 100 subject assignments can be created")
        List<@Valid CreateGradeLevelSubjectRequest> subjects
) {
}
