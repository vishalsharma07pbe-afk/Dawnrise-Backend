package com.dawnrise.academic.teacherassignment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateTeacherAssignmentsRequest(
        @NotEmpty(message = "Teacher assignments are required")
        @Size(
                max = 100,
                message = "At most 100 teacher assignments can be created"
        )
        List<@Valid BulkTeacherAssignmentItemRequest> assignments
) {
}
