package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;

public record StudentProgressionOperationRegistration(
        Long operationId,
        boolean replay,
        StudentProgressionConfirmationResponse replayResult
) {
}