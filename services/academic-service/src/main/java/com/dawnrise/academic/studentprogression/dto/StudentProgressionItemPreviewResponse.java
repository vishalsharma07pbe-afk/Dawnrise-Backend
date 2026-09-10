package com.dawnrise.academic.studentprogression.dto;

import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;

import java.time.LocalDate;
import java.util.List;

public record StudentProgressionItemPreviewResponse(

        Long sourceEnrollmentId,
        Long studentUserId,

        Long sourceGradeLevelId,
        Long sourceSectionId,
        String sourceRollNumber,
        StudentEnrollmentStatus sourceStatus,

        StudentProgressionOutcome outcome,

        Long targetGradeLevelId,
        Long targetSectionId,
        String targetRollNumber,

        Long suggestedTargetGradeLevelId,
        Long suggestedTargetSectionId,

        LocalDate effectiveOn,
        boolean canProgress,

        List<StudentProgressionConflictResponse> conflicts
) {
    public StudentProgressionItemPreviewResponse {
        conflicts = conflicts == null
                ? List.of()
                : List.copyOf(conflicts);
    }
}