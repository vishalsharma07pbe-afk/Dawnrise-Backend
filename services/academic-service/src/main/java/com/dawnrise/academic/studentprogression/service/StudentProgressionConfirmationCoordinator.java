package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;

public interface StudentProgressionConfirmationCoordinator {

    StudentProgressionConfirmationResponse confirm(
            long organizationId,
            long targetAcademicYearId,
            long authenticatedUserId,
            String idempotencyKey,
            StudentProgressionConfirmationRequest request
    );
}