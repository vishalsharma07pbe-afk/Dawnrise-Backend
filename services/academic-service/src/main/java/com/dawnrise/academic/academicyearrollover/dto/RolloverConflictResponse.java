package com.dawnrise.academic.academicyearrollover.dto;

public record RolloverConflictResponse(
        String type,
        String entityType,
        Long sourceId,
        String field,
        String value,
        String message
) {
}
