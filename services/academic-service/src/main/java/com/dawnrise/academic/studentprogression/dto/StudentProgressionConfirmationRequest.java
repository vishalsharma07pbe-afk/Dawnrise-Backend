package com.dawnrise.academic.studentprogression.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StudentProgressionConfirmationRequest(

        @Positive
        long sourceAcademicYearId,

        @NotBlank
        @Size(max = 100)
        String batchLabel,

        @NotEmpty
        @Size(max = 2000)
        @Valid
        List<StudentProgressionDecisionRequest> decisions,

        @NotBlank
        @Pattern(regexp = "^sha256:[0-9a-f]{64}$")
        String previewFingerprint
) {
    public StudentProgressionConfirmationRequest {
        if (decisions != null) {
            decisions = List.copyOf(decisions);
        }
    }
}