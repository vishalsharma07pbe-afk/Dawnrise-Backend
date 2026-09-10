package com.dawnrise.academic.studentprogression.dto;

public record StudentProgressionConflictResponse(
        String code,
        Long sourceEnrollmentId,
        String field,
        String message
) {
}