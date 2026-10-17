package com.dawnrise.academic.results.dto;

import java.util.List;

public record ResultPublicationReadinessResponse(
        long academicYearId,
        long examinationId,
        long gradeLevelId,
        long sectionId,
        boolean ready,
        boolean alreadyPublished,
        Long publicationId,
        int scheduledCount,
        int missingCount,
        int draftCount,
        int submittedCount,
        int returnedCount,
        int approvedCount,
        int incompleteRosterCount,
        List<String> blockers,
        List<ResultPublicationSubjectReadinessResponse> subjects
) {
}
