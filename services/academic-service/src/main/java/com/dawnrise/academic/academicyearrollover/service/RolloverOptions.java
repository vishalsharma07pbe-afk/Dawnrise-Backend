package com.dawnrise.academic.academicyearrollover.service;

import java.util.List;

public record RolloverOptions(
        Long sourceAcademicYearId,
        Long targetAcademicYearId,
        boolean includeGradeLevels,
        boolean includeSections,
        boolean includeSubjects,
        boolean includeGradeLevelSubjects,
        List<Long> gradeLevelIds,
        List<Long> subjectIds
) {
}
