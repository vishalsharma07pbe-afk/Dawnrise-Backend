package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;

public interface RolloverOperationLifecycleService {
    RolloverOperationRegistration registerOrResolve(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            String idempotencyKey,
            String requestHash,
            String previewFingerprint,
            long authenticatedUserId
    );

    void markTerminalFailure(
            long organizationId,
            long operationId,
            RolloverOperationStatus status,
            String failureCode,
            String failureMessage
    );
}
