package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;

public interface StudentProgressionOperationLifecycleService {

    StudentProgressionOperationRegistration registerOrResolve(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            String batchLabel,
            String idempotencyKey,
            String requestHash,
            String previewFingerprint,
            long authenticatedUserId,
            int totalItems
    );

    void markTerminalFailure(
            long organizationId,
            long operationId,
            StudentProgressionOperationStatus status,
            String failureCode,
            String failureMessage
    );
}