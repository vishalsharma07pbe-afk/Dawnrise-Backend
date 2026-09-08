package com.dawnrise.academic.academicyearrollover.exception;

import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;

public class RolloverOperationAlreadyRunningException
        extends RolloverConflictException {
    public RolloverOperationAlreadyRunningException(String message) {
        super(
                message,
                RolloverOperationStatus.CONFLICTED,
                "OPERATION_ALREADY_RUNNING"
        );
    }
}
