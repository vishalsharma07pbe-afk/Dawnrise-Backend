package com.dawnrise.academic.gradelevelsubject.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.dto.CreateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.dto.GradeLevelSubjectResponse;
import com.dawnrise.academic.gradelevelsubject.dto.UpdateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectConflictException;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.mapper.GradeLevelSubjectMapper;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.gradelevelsubject.service.GradeLevelSubjectService;
import com.dawnrise.academic.subject.exception.SubjectNotFoundException;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class GradeLevelSubjectServiceImpl
        implements GradeLevelSubjectService {

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