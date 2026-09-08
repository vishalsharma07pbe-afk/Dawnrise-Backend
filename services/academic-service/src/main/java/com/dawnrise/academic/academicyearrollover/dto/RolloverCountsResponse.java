package com.dawnrise.academic.academicyearrollover.dto;

public record RolloverCountsResponse(
        int gradeLevels,
        int sections,
        int subjects,
        int gradeLevelSubjects,
        int totalGeneratedRecords
) {
}
