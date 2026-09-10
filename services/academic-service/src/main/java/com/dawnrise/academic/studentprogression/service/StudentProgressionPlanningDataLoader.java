package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;

import java.util.List;

public interface StudentProgressionPlanningDataLoader {

    StudentProgressionPlanningData loadForPreview(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            List<StudentProgressionDecisionRequest> decisions
    );

    StudentProgressionPlanningData loadForConfirmation(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            List<StudentProgressionDecisionRequest> decisions
    );
}