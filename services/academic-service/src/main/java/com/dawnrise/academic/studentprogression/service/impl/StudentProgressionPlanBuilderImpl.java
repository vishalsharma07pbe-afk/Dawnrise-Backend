package com.dawnrise.academic.studentprogression.service.impl;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionPreviewRequest;
import com.dawnrise.academic.studentprogression.service.StudentProgressionFingerprintService;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlan;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanBuilder;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanValidator;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanningData;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanningDataLoader;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StudentProgressionPlanBuilderImpl
        implements StudentProgressionPlanBuilder {

    private final StudentProgressionPlanningDataLoader dataLoader;
    private final StudentProgressionPlanValidator validator;
    private final StudentProgressionFingerprintService fingerprintService;

    public StudentProgressionPlanBuilderImpl(
            StudentProgressionPlanningDataLoader dataLoader,
            StudentProgressionPlanValidator validator,
            StudentProgressionFingerprintService fingerprintService
    ) {
        this.dataLoader = dataLoader;
        this.validator = validator;
        this.fingerprintService = fingerprintService;
    }

    @Override
    public StudentProgressionPlan buildForPreview(
            long organizationId,
            long targetAcademicYearId,
            StudentProgressionPreviewRequest request
    ) {
        StudentProgressionPlanningData data =
                dataLoader.loadForPreview(
                        organizationId,
                        request.sourceAcademicYearId(),
                        targetAcademicYearId,
                        request.decisions()
                );

        return buildPlan(
                organizationId,
                targetAcademicYearId,
                request.sourceAcademicYearId(),
                request.batchLabel(),
                request.decisions(),
                data,
                false
        );
    }

    @Override
    public StudentProgressionPlan buildForConfirmation(
            long organizationId,
            long targetAcademicYearId,
            StudentProgressionConfirmationRequest request
    ) {
        StudentProgressionPlanningData data =
                dataLoader.loadForConfirmation(
                        organizationId,
                        request.sourceAcademicYearId(),
                        targetAcademicYearId,
                        request.decisions()
                );

        return buildPlan(
                organizationId,
                targetAcademicYearId,
                request.sourceAcademicYearId(),
                request.batchLabel(),
                request.decisions(),
                data,
                true
        );
    }

    private StudentProgressionPlan buildPlan(
            long organizationId,
            long targetAcademicYearId,
            long sourceAcademicYearId,
            String batchLabel,
            List<StudentProgressionDecisionRequest> decisions,
            StudentProgressionPlanningData data,
            boolean confirmation
    ) {
        StudentProgressionPlan planWithoutFingerprint =
                validator.validate(
                        data,
                        batchLabel,
                        decisions,
                        confirmation
                );

        String requestHash = fingerprintService.requestHash(
                organizationId,
                sourceAcademicYearId,
                targetAcademicYearId,
                batchLabel,
                decisions
        );

        String previewFingerprint =
                fingerprintService.previewFingerprint(
                        requestHash,
                        planWithoutFingerprint
                );

        return new StudentProgressionPlan(
                planWithoutFingerprint.sourceAcademicYear(),
                planWithoutFingerprint.targetAcademicYear(),
                planWithoutFingerprint.batchLabel(),
                planWithoutFingerprint.items(),
                planWithoutFingerprint.conflicts(),
                previewFingerprint
        );
    }
}