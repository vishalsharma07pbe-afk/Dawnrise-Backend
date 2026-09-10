package com.dawnrise.academic.studentprogression.dto;

import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;

public record StudentProgressionItemResultResponse(

        Long sourceEnrollmentId,
        Long studentUserId,
        StudentProgressionOutcome outcome,
        StudentEnrollmentStatus sourceEnrollmentStatus,
        Long targetEnrollmentId
) {
}