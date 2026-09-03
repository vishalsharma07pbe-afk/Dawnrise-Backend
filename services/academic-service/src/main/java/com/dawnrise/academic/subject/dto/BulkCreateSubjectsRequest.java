package com.dawnrise.academic.subject.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateSubjectsRequest(
        @NotEmpty(message = "At least one subject is required")
        @Size(max = 100, message = "A maximum of 100 subjects can be created")
        List<@Valid CreateSubjectRequest> subjects
) {
}
