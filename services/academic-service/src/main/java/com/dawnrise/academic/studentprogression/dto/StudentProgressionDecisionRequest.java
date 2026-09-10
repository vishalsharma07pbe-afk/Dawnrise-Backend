package com.dawnrise.academic.studentprogression.dto;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StudentProgressionDecisionRequest(

        @NotNull
        @Positive
        Long sourceEnrollmentId,

        @NotNull
        StudentProgressionOutcome outcome,

        @Positive
        Long targetGradeLevelId,

        @Positive
        Long targetSectionId,

        @Size(max = 30)
        String targetRollNumber,

        @NotNull
        LocalDate effectiveOn,

        @Size(max = 500)
        String note
) {
}