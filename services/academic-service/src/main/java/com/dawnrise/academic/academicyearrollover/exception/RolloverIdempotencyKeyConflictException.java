package com.dawnrise.academic.academicyearrollover.exception;

import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;

public class RolloverIdempotencyKeyConflictException
        extends RolloverConflictException {
    public RolloverIdempotencyKeyConflictException(String message) {
        super(
                message,
                RolloverOperationStatus.CONFLICTED,
                "IDEMPOTENCY_KEY_CONFLICT"
        );
    }
}
