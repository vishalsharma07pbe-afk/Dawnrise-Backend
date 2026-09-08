package com.dawnrise.academic.academicyearrollover.exception;

import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;

public class StaleRolloverPreviewException extends RolloverConflictException {
    public StaleRolloverPreviewException(String message) {
        super(
                message,
                RolloverOperationStatus.STALE_PREVIEW,
                "STALE_PREVIEW"
        );
    }
}
