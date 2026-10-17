package com.dawnrise.academic.results.dto;

import com.dawnrise.academic.results.enums.ResultSubjectSheetStatus;

public record ResultPublicationSubjectReadinessResponse(
        long scheduledAssessmentId,
        long gradeLevelSubjectId,
        long subjectId,
        String subjectCode,
        String subjectName,
        ResultSubjectSheetStatus sheetStatus,
        boolean rosterComplete,
        String blocker
) {
}
