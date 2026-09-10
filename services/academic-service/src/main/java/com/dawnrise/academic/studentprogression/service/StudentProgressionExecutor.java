package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;

public interface StudentProgressionExecutor {

    StudentProgressionConfirmationResponse execute(
            long organizationId,
            long operationId,
            long targetAcademicYearId,
            String requestHash,
            StudentProgressionConfirmationRequest request
    );
}