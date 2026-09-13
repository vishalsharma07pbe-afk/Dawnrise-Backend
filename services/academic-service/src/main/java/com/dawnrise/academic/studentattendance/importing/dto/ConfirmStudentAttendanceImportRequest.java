package com.dawnrise.academic.studentattendance.importing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmStudentAttendanceImportRequest(
        @NotBlank
        @Pattern(regexp = "^sha256:[0-9a-f]{64}$")
        String previewFingerprint
) {
}
