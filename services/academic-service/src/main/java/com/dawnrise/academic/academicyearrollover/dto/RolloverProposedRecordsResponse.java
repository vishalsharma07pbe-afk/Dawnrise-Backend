package com.dawnrise.academic.academicyearrollover.dto;

import java.util.List;

public record RolloverProposedRecordsResponse(
        List<ProposedGradeLevelResponse> gradeLevels,
        List<ProposedSectionResponse> sections,
        List<ProposedSubjectResponse> subjects,
        List<ProposedGradeLevelSubjectResponse> gradeLevelSubjects
) {
}
