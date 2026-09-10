package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionPreviewRequest;

public interface StudentProgressionPlanBuilder {

    StudentProgressionPlan buildForPreview(
            long organizationId,
            long targetAcademicYearId,
            StudentProgressionPreviewRequest request
    );

    StudentProgressionPlan buildForConfirmation(
            long organizationId,
            long targetAcademicYearId,
            StudentProgressionConfirmationRequest request
    );
}