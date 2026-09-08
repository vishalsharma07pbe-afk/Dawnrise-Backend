package com.dawnrise.academic.academicyearrollover.exception;

import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;

public class RolloverConflictException extends RuntimeException {
    private final RolloverOperationStatus status;
    private final String failureCode;

    public RolloverConflictException(
            String message,
            RolloverOperationStatus status,
            String failureCode
    ) {
        super(message);
        this.status = status;
        this.failureCode = failureCode;
    }

    public RolloverOperationStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
