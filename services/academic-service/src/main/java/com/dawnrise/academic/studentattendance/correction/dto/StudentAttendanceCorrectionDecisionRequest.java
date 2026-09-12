package com.dawnrise.academic.studentattendance.correction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentAttendanceCorrectionDecisionRequest(
        @NotNull Long expectedVersion,
        @Size(max = 500) String reviewComment
) {
}
