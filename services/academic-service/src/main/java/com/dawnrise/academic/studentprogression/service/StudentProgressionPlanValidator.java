package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;

import java.util.List;

public interface StudentProgressionPlanValidator {

    StudentProgressionPlan validate(
            StudentProgressionPlanningData data,
            String batchLabel,
            List<StudentProgressionDecisionRequest> decisions,
            boolean confirmation
    );
}