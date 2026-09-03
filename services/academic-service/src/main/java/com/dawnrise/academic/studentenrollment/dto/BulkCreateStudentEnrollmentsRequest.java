package com.dawnrise.academic.studentenrollment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateStudentEnrollmentsRequest(
        @NotEmpty(message = "Student enrollments are required")
        @Size(
                max = 100,
                message = "At most 100 student enrollments can be created"
        )
        List<@Valid CreateStudentEnrollmentRequest> enrollments
) {
}
