package com.dawnrise.academic.academicyearrollover.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.academicyearrollover.dto.*;
import com.dawnrise.academic.academicyearrollover.entity.AcademicYearStructureRolloverOperation;
import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;
import com.dawnrise.academic.academicyearrollover.exception.RolloverConflictException;
import com.dawnrise.academic.academicyearrollover.exception.StaleRolloverPreviewException;
import com.dawnrise.academic.academicyearrollover.repository.AcademicYearStructureRolloverOperationRepository;
import com.dawnrise.academic.academicyearrollover.service.*;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RolloverExecutorImpl implements RolloverExecutor {

    private final AcademicYearStructureRolloverOperationRepository operationRepository;
    private final AcademicYearRepository academicYearRepository;
    private final RolloverPlanBuilder planBuilder;
    private final SubjectRepository subjectRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SectionRepository sectionRepository;
    private final GradeLevelSubjectRepository gradeLevelSubjectRepository;
    private final ObjectMapper objectMapper;

    public RolloverExecutorImpl(
            AcademicYearStructureRolloverOperationRepository operationRepository,
            AcademicYearRepository academicYearRepository,
            RolloverPlanBuilder planBuilder,
            SubjectRepository subjectRepository,
            GradeLevelRepository gradeLevelRepository,
            SectionRepository sectionRepository,
            GradeLevelSubjectRepository gradeLevelSubjectRepository,
            ObjectMapper objectMapper
    ) {
        this.operationRepository = operationRepository;
        this.academicYearRepository = academicYearRepository;
        this.planBuilder = planBuilder;
        this.subjectRepository = subjectRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.sectionRepository = sectionRepository;
        this.gradeLevelSubjectRepository = gradeLevelSubjectRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AcademicYearStructureRolloverResultResponse execute(
            long organizationId,
            long operationId,
            String submittedPreviewFingerprint,
            AcademicYearStructureRolloverRequest request
    ) {
        AcademicYearStructureRolloverOperation operation =
                operationRepository
                        .findByIdAndOrganizationIdForUpdate(
                                operationId,
                                organizationId
                        )
                        .orElseThrow(() -> new RolloverConflictException(
                                "Rollover operation not found",
                                RolloverOperationStatus.FAILED,
                                "OPERATION_NOT_FOUND"
                        ));
        AcademicYear target = academicYearRepository
                .findByIdAndOrganizationIdForUpdate(
                        operation.getTargetAcademicYearId(),
                        organizationId
                )
                .orElseThrow(() -> new AcademicYearNotFoundException(
                        "Academic year not found"
                ));

        RolloverPlan plan = planBuilder.build(
                organizationId,
                target.getId(),
                request
        );

        if (!Objects.equals(plan.requestHash(), operation.getRequestHash())) {
            throw new StaleRolloverPreviewException(
                    "Rollover request no longer matches the registered operation"
            );
        }
        if (!Objects.equals(plan.previewFingerprint(), submittedPreviewFingerprint)) {
            throw new StaleRolloverPreviewException(
                    "Rollover preview is stale"
            );
        }
        if (!plan.conflicts().isEmpty()) {
            throw new RolloverConflictException(
                    "Target academic year contains conflicting structure",
                    RolloverOperationStatus.CONFLICTED,
                    "TARGET_CONFLICT"
            );
        }

        try {
            return persistPlan(operation, plan);
        } catch (DataIntegrityViolationException exception) {
            throw new RolloverConflictException(
                    "Academic structure changed during rollover confirmation",
                    RolloverOperationStatus.CONFLICTED,
                    "CONCURRENT_MODIFICATION"
            );
        }
    }

    private AcademicYearStructureRolloverResultResponse persistPlan(
            AcademicYearStructureRolloverOperation operation,
            RolloverPlan plan
    ) {
        Map<Long, Long> subjectMap = new LinkedHashMap<>();
        List<Subject> newSubjects = plan.sourceSubjects().stream()
                .map(subject -> new Subject(
                        operation.getOrganizationId(),
                        operation.getTargetAcademicYearId(),
                        subject.getCode(),
                        subject.getName(),
                        subject.getDescription()
                ))
                .toList();
        List<Subject> savedSubjects = subjectRepository.saveAll(newSubjects);
        for (int i = 0; i < plan.sourceSubjects().size(); i++) {
            subjectMap.put(
                    plan.sourceSubjects().get(i).getId(),
                    savedSubjects.get(i).getId()
            );
        }

        Map<Long, Long> gradeMap = new LinkedHashMap<>();
        List<GradeLevel> newGrades = plan.sourceGradeLevels().stream()
                .map(grade -> new GradeLevel(
                        operation.getOrganizationId(),
                        operation.getTargetAcademicYearId(),
                        grade.getCode(),
                        grade.getName(),
                        grade.getDisplayOrder()
                ))
                .toList();
        List<GradeLevel> savedGrades = gradeLevelRepository.saveAll(newGrades);
        for (int i = 0; i < plan.sourceGradeLevels().size(); i++) {
            gradeMap.put(
                    plan.sourceGradeLevels().get(i).getId(),
                    savedGrades.get(i).getId()
            );
        }

        Map<Long, Long> sectionMap = new LinkedHashMap<>();
        List<Section> newSections = plan.sourceSections().stream()
                .map(section -> new Section(
                        operation.getOrganizationId(),
                        operation.getTargetAcademicYearId(),
                        gradeMap.get(section.getGradeLevelId()),
                        section.getCode(),
                        section.getName(),
                        section.getDisplayOrder()
                ))
                .toList();
        List<Section> savedSections = sectionRepository.saveAll(newSections);
        for (int i = 0; i < plan.sourceSections().size(); i++) {
            sectionMap.put(
                    plan.sourceSections().get(i).getId(),
                    savedSections.get(i).getId()
            );
        }

        Map<Long, Long> gradeSubjectMap = new LinkedHashMap<>();
        List<GradeLevelSubject> newAssignments =
                plan.sourceGradeLevelSubjects().stream()
                        .map(assignment -> new GradeLevelSubject(
                                operation.getOrganizationId(),
                                operation.getTargetAcademicYearId(),
                                gradeMap.get(assignment.getGradeLevelId()),
                                subjectMap.get(assignment.getSubjectId()),
                                assignment.getMandatory(),
                                assignment.getDisplayOrder()
                        ))
                        .toList();
        List<GradeLevelSubject> savedAssignments =
                gradeLevelSubjectRepository.saveAll(newAssignments);
        for (int i = 0; i < plan.sourceGradeLevelSubjects().size(); i++) {
            gradeSubjectMap.put(
                    plan.sourceGradeLevelSubjects().get(i).getId(),
                    savedAssignments.get(i).getId()
            );
        }

        subjectRepository.flush();
        gradeLevelRepository.flush();
        sectionRepository.flush();
        gradeLevelSubjectRepository.flush();

        AcademicYearStructureRolloverResultResponse response =
                new AcademicYearStructureRolloverResultResponse(
                        operation.getId(),
                        RolloverOperationStatus.SUCCEEDED.name(),
                        operation.getSourceAcademicYearId(),
                        operation.getTargetAcademicYearId(),
                        plan.requestHash(),
                        plan.previewFingerprint(),
                        plan.counts(),
                        new RolloverMappingsResponse(
                                toMappings(gradeMap),
                                toMappings(sectionMap),
                                toMappings(subjectMap),
                                toMappings(gradeSubjectMap)
                        )
                );
        try {
            operation.markSucceeded(objectMapper.writeValueAsString(response));
        } catch (Exception exception) {
            throw new RolloverConflictException(
                    "Rollover result could not be stored",
                    RolloverOperationStatus.FAILED,
                    "RESULT_SERIALIZATION_FAILED"
            );
        }
        operationRepository.saveAndFlush(operation);
        return response;
    }

    private List<RolloverIdMappingResponse> toMappings(Map<Long, Long> map) {
        return map.entrySet()
                .stream()
                .map(entry -> new RolloverIdMappingResponse(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }
}
