package com.dawnrise.academic.studentprogression.exception;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;

public class StudentProgressionIdempotencyKeyConflictException
        extends StudentProgressionConflictException {

    public StudentProgressionIdempotencyKeyConflictException(
            String message
    ) {
        super(
                message,
                StudentProgressionOperationStatus.CONFLICTED,
                "IDEMPOTENCY_KEY_REUSED"
        );
    }
}