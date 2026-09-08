package com.dawnrise.academic.academicyearrollover.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record AcademicYearStructureRolloverRequest(
        @NotNull(message = "Source academic year ID is required")
        @Positive(message = "Source academic year ID must be positive")
        Long sourceAcademicYearId,
        Boolean includeGradeLevels,
        Boolean includeSections,
        Boolean includeSubjects,
        Boolean includeGradeLevelSubjects,
        List<Long> gradeLevelIds,
        List<Long> subjectIds
) {
}
