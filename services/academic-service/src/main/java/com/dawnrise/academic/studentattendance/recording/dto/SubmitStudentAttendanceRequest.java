package com.dawnrise.academic.studentattendance.recording.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitStudentAttendanceRequest(
        @NotNull @Min(0) Long expectedSessionVersion
) {
}
