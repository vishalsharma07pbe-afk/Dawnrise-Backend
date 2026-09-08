package com.dawnrise.academic.academicyearrollover.dto;

import java.util.List;

public record RolloverMappingsResponse(
        List<RolloverIdMappingResponse> gradeLevels,
        List<RolloverIdMappingResponse> sections,
        List<RolloverIdMappingResponse> subjects,
        List<RolloverIdMappingResponse> gradeLevelSubjects
) {
}
