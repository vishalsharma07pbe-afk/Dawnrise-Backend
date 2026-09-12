package com.dawnrise.academic.studentattendance.correction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStudentAttendanceCorrectionRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotEmpty @Size(max = 100) List<@Valid StudentAttendanceCorrectionItemRequest> items
) {
}
