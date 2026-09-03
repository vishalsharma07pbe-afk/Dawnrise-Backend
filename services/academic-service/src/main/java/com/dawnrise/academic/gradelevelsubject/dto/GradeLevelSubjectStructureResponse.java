package com.dawnrise.academic.gradelevelsubject.dto;

import java.util.List;

public record GradeLevelSubjectStructureResponse(
        Long gradeLevelId,
        List<GradeLevelSubjectResponse> subjects
) {
}
