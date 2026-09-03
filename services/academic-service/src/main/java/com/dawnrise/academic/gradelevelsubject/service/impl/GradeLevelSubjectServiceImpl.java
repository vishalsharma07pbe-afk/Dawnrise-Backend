package com.dawnrise.academic.gradelevelsubject.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.dto.*;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectConflictException;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.mapper.GradeLevelSubjectMapper;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.gradelevelsubject.service.GradeLevelSubjectService;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.exception.SubjectNotFoundException;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class GradeLevelSubjectServiceImpl
        implements GradeLevelSubjectService {

    private static final int MAX_GENERATED_STRUCTURE_MAPPINGS = 2_000;

    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SubjectRepository subjectRepository;
    private final GradeLevelSubjectRepository assignmentRepository;
    private final GradeLevelSubjectMapper assignmentMapper;

    public GradeLevelSubjectServiceImpl(
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SubjectRepository subjectRepository,
            GradeLevelSubjectRepository assignmentRepository,
            GradeLevelSubjectMapper assignmentMapper
    ) {
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.subjectRepository = subjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentMapper = assignmentMapper;
    }

    @Override
    public GradeLevelSubjectResponse assign(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            CreateGradeLevelSubjectRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        findGradeLevel(organizationId, academicYearId, gradeLevelId);
        findSubject(organizationId, academicYearId, request.subjectId());

        if (assignmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSubjectId(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        request.subjectId()
                )) {
            throw new GradeLevelSubjectConflictException(
                    "This subject is already assigned to the grade level"
            );
        }

        if (assignmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrder(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        request.displayOrder()
                )) {
            throw new GradeLevelSubjectConflictException(
                    "This display order is already used by another subject"
            );
        }

        GradeLevelSubject assignment = new GradeLevelSubject(
                organizationId,
                academicYearId,
                gradeLevelId,
                request.subjectId(),
                request.mandatory(),
                request.displayOrder()
        );

        GradeLevelSubject savedAssignment =
                assignmentRepository.saveAndFlush(assignment);

        return assignmentMapper.toResponse(savedAssignment);
    }

    @Override
    public List<GradeLevelSubjectResponse> assignBulk(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            BulkCreateGradeLevelSubjectsRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        findGradeLevel(organizationId, academicYearId, gradeLevelId);
        validateStructureDefinitions(request.subjects());
        validateSubjectsExist(
                organizationId,
                academicYearId,
                request.subjects()
                        .stream()
                        .map(CreateGradeLevelSubjectRequest::subjectId)
                        .toList()
        );

        List<GradeLevelSubject> existingAssignments =
                assignmentRepository
                        .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdIn(
                                organizationId,
                                academicYearId,
                                List.of(gradeLevelId)
                        );

        rejectExistingConflicts(
                List.of(gradeLevelId),
                request.subjects(),
                existingAssignments
        );

        List<GradeLevelSubject> assignments =
                request.subjects()
                        .stream()
                        .map(item -> new GradeLevelSubject(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                item.subjectId(),
                                item.mandatory(),
                                item.displayOrder()
                        ))
                        .toList();

        return assignmentRepository.saveAllAndFlush(assignments)
                .stream()
                .map(assignmentMapper::toResponse)
                .toList();
    }

    @Override
    public List<GradeLevelSubjectStructureResponse> applyStructure(
            long organizationId,
            long academicYearId,
            ApplyGradeLevelSubjectStructureRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        validateTargetGradeIds(request.gradeLevelIds());
        validateStructureDefinitions(request.subjects());
        validateGeneratedMappingLimit(request);
        validateGradesExist(
                organizationId,
                academicYearId,
                request.gradeLevelIds()
        );
        validateSubjectsExist(
                organizationId,
                academicYearId,
                request.subjects()
                        .stream()
                        .map(CreateGradeLevelSubjectRequest::subjectId)
                        .toList()
        );

        List<GradeLevelSubject> existingAssignments =
                assignmentRepository
                        .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdIn(
                                organizationId,
                                academicYearId,
                                request.gradeLevelIds()
                        );

        rejectExistingConflicts(
                request.gradeLevelIds(),
                request.subjects(),
                existingAssignments
        );

        List<GradeLevelSubject> generatedAssignments =
                new ArrayList<>();

        for (Long gradeLevelId : request.gradeLevelIds()) {
            for (CreateGradeLevelSubjectRequest subject
                    : request.subjects()) {
                generatedAssignments.add(new GradeLevelSubject(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        subject.subjectId(),
                        subject.mandatory(),
                        subject.displayOrder()
                ));
            }
        }

        List<GradeLevelSubjectResponse> savedResponses =
                assignmentRepository.saveAllAndFlush(generatedAssignments)
                        .stream()
                        .map(assignmentMapper::toResponse)
                        .toList();

        List<GradeLevelSubjectStructureResponse> response =
                new ArrayList<>();
        int index = 0;
        for (Long gradeLevelId : request.gradeLevelIds()) {
            List<GradeLevelSubjectResponse> gradeResponses =
                    new ArrayList<>();
            for (int i = 0; i < request.subjects().size(); i++) {
                gradeResponses.add(savedResponses.get(index++));
            }
            response.add(new GradeLevelSubjectStructureResponse(
                    gradeLevelId,
                    List.copyOf(gradeResponses)
            ));
        }

        return List.copyOf(response);
    }

    @Override
    @Transactional(readOnly = true)
    public GradeLevelSubjectResponse getById(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long assignmentId
    ) {
        findAcademicYear(organizationId, academicYearId);
        findGradeLevel(organizationId, academicYearId, gradeLevelId);

        return assignmentMapper.toResponse(
                findAssignment(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        assignmentId
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<GradeLevelSubjectResponse> getAll(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    ) {
        findAcademicYear(organizationId, academicYearId);
        findGradeLevel(organizationId, academicYearId, gradeLevelId);

        return assignmentRepository
                .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdOrderByDisplayOrderAsc(
                        organizationId,
                        academicYearId,
                        gradeLevelId
                )
                .stream()
                .map(assignmentMapper::toResponse)
                .toList();
    }

    @Override
    public GradeLevelSubjectResponse update(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long assignmentId,
            UpdateGradeLevelSubjectRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        findGradeLevel(organizationId, academicYearId, gradeLevelId);

        GradeLevelSubject assignment = findAssignment(
                organizationId,
                academicYearId,
                gradeLevelId,
                assignmentId
        );

        if (!Objects.equals(
                assignment.getVersion(),
                request.version()
        )) {
            throw new GradeLevelSubjectConflictException(
                    "Grade-subject assignment was modified by another request"
            );
        }

        if (assignmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrderAndIdNot(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        request.displayOrder(),
                        assignmentId
                )) {
            throw new GradeLevelSubjectConflictException(
                    "This display order is already used by another subject"
            );
        }

        assignment.updateDetails(
                request.mandatory(),
                request.displayOrder()
        );

        GradeLevelSubject savedAssignment =
                assignmentRepository.saveAndFlush(assignment);

        return assignmentMapper.toResponse(savedAssignment);
    }

    @Override
    public void remove(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long assignmentId
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        findGradeLevel(organizationId, academicYearId, gradeLevelId);

        GradeLevelSubject assignment = findAssignment(
                organizationId,
                academicYearId,
                gradeLevelId,
                assignmentId
        );

        assignmentRepository.delete(assignment);
        assignmentRepository.flush();
    }

    private AcademicYear findAcademicYear(
            long organizationId,
            long academicYearId
    ) {
        return academicYearRepository
                .findByIdAndOrganizationId(
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new AcademicYearNotFoundException(
                                "Academic year not found"
                        )
                );
    }

    private void findGradeLevel(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    ) {
        gradeLevelRepository
                .findByIdAndAcademicYearIdAndOrganizationId(
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new GradeLevelNotFoundException(
                                "Grade level not found"
                        )
                );
    }

    private void findSubject(
            long organizationId,
            long academicYearId,
            long subjectId
    ) {
        subjectRepository
                .findByIdAndAcademicYearIdAndOrganizationId(
                        subjectId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new SubjectNotFoundException(
                                "Subject not found"
                        )
                );
    }

    private void validateGradesExist(
            long organizationId,
            long academicYearId,
            List<Long> gradeLevelIds
    ) {
        Map<Long, GradeLevel> gradesById =
                gradeLevelRepository
                        .findAllByOrganizationIdAndAcademicYearIdAndIdIn(
                                organizationId,
                                academicYearId,
                                gradeLevelIds
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                GradeLevel::getId,
                                Function.identity()
                        ));

        for (Long gradeLevelId : gradeLevelIds) {
            if (!gradesById.containsKey(gradeLevelId)) {
                throw new GradeLevelNotFoundException(
                        "Grade level not found"
                );
            }
        }
    }

    private void validateSubjectsExist(
            long organizationId,
            long academicYearId,
            List<Long> subjectIds
    ) {
        Map<Long, Subject> subjectsById =
                subjectRepository
                        .findAllByOrganizationIdAndAcademicYearIdAndIdIn(
                                organizationId,
                                academicYearId,
                                subjectIds
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                Subject::getId,
                                Function.identity()
                        ));

        for (Long subjectId : subjectIds) {
            if (!subjectsById.containsKey(subjectId)) {
                throw new SubjectNotFoundException(
                        "Subject not found"
                );
            }
        }
    }

    private void validateTargetGradeIds(List<Long> gradeLevelIds) {
        Set<Long> uniqueGradeLevelIds = new HashSet<>();

        for (Long gradeLevelId : gradeLevelIds) {
            if (!uniqueGradeLevelIds.add(gradeLevelId)) {
                throw new GradeLevelSubjectConflictException(
                        "The request contains a duplicate grade level ID"
                );
            }
        }
    }

    private void validateStructureDefinitions(
            List<CreateGradeLevelSubjectRequest> subjects
    ) {
        Set<Long> subjectIds = new HashSet<>();
        Set<Integer> displayOrders = new HashSet<>();

        for (CreateGradeLevelSubjectRequest subject : subjects) {
            if (!subjectIds.add(subject.subjectId())) {
                throw new GradeLevelSubjectConflictException(
                        "The request contains a duplicate subject ID"
                );
            }

            if (!displayOrders.add(subject.displayOrder())) {
                throw new GradeLevelSubjectConflictException(
                        "The request contains a duplicate display order"
                );
            }
        }
    }

    private void validateGeneratedMappingLimit(
            ApplyGradeLevelSubjectStructureRequest request
    ) {
        int generatedMappings =
                request.gradeLevelIds().size()
                        * request.subjects().size();

        if (generatedMappings > MAX_GENERATED_STRUCTURE_MAPPINGS) {
            throw new GradeLevelSubjectConflictException(
                    "The request would create too many subject assignments"
            );
        }
    }

    private void rejectExistingConflicts(
            List<Long> gradeLevelIds,
            List<CreateGradeLevelSubjectRequest> subjects,
            List<GradeLevelSubject> existingAssignments
    ) {
        Set<GradeSubjectKey> existingSubjectKeys =
                existingAssignments.stream()
                        .map(assignment -> new GradeSubjectKey(
                                assignment.getGradeLevelId(),
                                assignment.getSubjectId()
                        ))
                        .collect(Collectors.toSet());
        Set<GradeDisplayOrderKey> existingDisplayOrderKeys =
                existingAssignments.stream()
                        .map(assignment -> new GradeDisplayOrderKey(
                                assignment.getGradeLevelId(),
                                assignment.getDisplayOrder()
                        ))
                        .collect(Collectors.toSet());

        for (Long gradeLevelId : gradeLevelIds) {
            for (CreateGradeLevelSubjectRequest subject : subjects) {
                if (existingSubjectKeys.contains(new GradeSubjectKey(
                        gradeLevelId,
                        subject.subjectId()
                ))) {
                    throw new GradeLevelSubjectConflictException(
                            "This subject is already assigned to the grade level"
                    );
                }

                if (existingDisplayOrderKeys.contains(
                        new GradeDisplayOrderKey(
                                gradeLevelId,
                                subject.displayOrder()
                        )
                )) {
                    throw new GradeLevelSubjectConflictException(
                            "This display order is already used by another subject"
                    );
                }
            }
        }
    }

    private record GradeSubjectKey(
            Long gradeLevelId,
            Long subjectId
    ) {
    }

    private record GradeDisplayOrderKey(
            Long gradeLevelId,
            Integer displayOrder
    ) {
    }

    private GradeLevelSubject findAssignment(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long assignmentId
    ) {
        return assignmentRepository
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        assignmentId,
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new GradeLevelSubjectNotFoundException(
                                "Grade-subject assignment not found"
                        )
                );
    }

    private void ensureAcademicYearIsModifiable(
            AcademicYear academicYear
    ) {
        if (academicYear.getStatus() != AcademicYearStatus.PLANNED
                && academicYear.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new GradeLevelSubjectConflictException(
                    "Subject assignments can only be modified for a planned or active academic year"
            );
        }
    }
}
