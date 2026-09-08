package com.dawnrise.academic.academicyearrollover.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ConfirmAcademicYearStructureRolloverRequest(
        @NotNull(message = "Source academic year ID is required")
        @Positive(message = "Source academic year ID must be positive")
        Long sourceAcademicYearId,
        Boolean includeGradeLevels,
        Boolean includeSections,
        Boolean includeSubjects,
        Boolean includeGradeLevelSubjects,
        List<Long> gradeLevelIds,
        List<Long> subjectIds,
        @NotBlank(message = "Preview fingerprint is required")
        @Pattern(
                regexp = "^sha256:[0-9a-f]{64}$",
                message = "Preview fingerprint is invalid"
        )
        String previewFingerprint
) {
}
