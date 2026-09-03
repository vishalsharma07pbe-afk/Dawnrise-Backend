package com.dawnrise.academic.teacherassignment.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.exception.SectionNotFoundException;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.teacherassignment.dto.CreateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.dto.TeacherAssignmentResponse;
import com.dawnrise.academic.teacherassignment.dto.UpdateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.entity.TeacherAssignment;
import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import com.dawnrise.academic.teacherassignment.exception.TeacherAssignmentConflictException;
import com.dawnrise.academic.teacherassignment.exception.TeacherAssignmentNotFoundException;
import com.dawnrise.academic.teacherassignment.exception.TeacherNotEligibleException;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityReason;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse;
import com.dawnrise.academic.teacherassignment.mapper.TeacherAssignmentMapper;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import com.dawnrise.academic.teacherassignment.service.TeacherAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class TeacherAssignmentServiceImpl
        implements TeacherAssignmentService {

    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SectionRepository sectionRepository;
    private final GradeLevelSubjectRepository gradeSubjectRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final IdentityTeacherEligibilityClient eligibilityClient;
    private final TeacherAssignmentMapper assignmentMapper;

    public TeacherAssignmentServiceImpl(
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SectionRepository sectionRepository,
            GradeLevelSubjectRepository gradeSubjectRepository,
            TeacherAssignmentRepository assignmentRepository,
            IdentityTeacherEligibilityClient eligibilityClient,
            TeacherAssignmentMapper assignmentMapper
    ) {
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.sectionRepository = sectionRepository;
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.eligibilityClient = eligibilityClient;
        this.assignmentMapper = assignmentMapper;
    }

    @Override
    public TeacherAssignmentResponse create(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            CreateTeacherAssignmentRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        findGradeLevel(organizationId, academicYearId, gradeLevelId);
        findSection(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );

        validateAssignmentTarget(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                request
        );

        validateTeacherEligibility(
                organizationId,
                request.teacherUserId()
        );

        TeacherAssignment assignment = new TeacherAssignment(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                request.gradeLevelSubjectId(),
                request.teacherUserId(),
                request.assignmentType()
        );

        TeacherAssignment savedAssignment =
                assignmentRepository.saveAndFlush(assignment);

        return assignmentMapper.toResponse(savedAssignment);
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherAssignmentResponse getById(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long assignmentId
    ) {
        return assignmentMapper.toResponse(
                findAssignment(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        assignmentId
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> getAll(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    ) {
        findAcademicYear(organizationId, academicYearId);
        findGradeLevel(organizationId, academicYearId, gradeLevelId);
        findSection(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );

        return assignmentRepository
                .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdOrderByAssignmentTypeAscIdAsc(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        sectionId
                )
                .stream()
                .map(assignmentMapper::toResponse)
                .toList();
    }

    @Override
    public TeacherAssignmentResponse update(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long assignmentId,
            UpdateTeacherAssignmentRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);

        TeacherAssignment assignment = findAssignment(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                assignmentId
        );

        if (!Objects.equals(
                assignment.getVersion(),
                request.version()
        )) {
            throw new TeacherAssignmentConflictException(
                    "Teacher assignment was modified by another request"
            );
        }

        validateTeacherEligibility(
                organizationId,
                request.teacherUserId()
        );

        assignment.replaceTeacher(request.teacherUserId());

        TeacherAssignment savedAssignment =
                assignmentRepository.saveAndFlush(assignment);

        return assignmentMapper.toResponse(savedAssignment);
    }

    @Override
    public void remove(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long assignmentId
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);

        TeacherAssignment assignment = findAssignment(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                assignmentId
        );

        assignmentRepository.delete(assignment);
        assignmentRepository.flush();
    }

    private void validateAssignmentTarget(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            CreateTeacherAssignmentRequest request
    ) {
        if (request.assignmentType()
                == TeacherAssignmentType.CLASS_TEACHER) {

            if (request.gradeLevelSubjectId() != null) {
                throw new TeacherAssignmentConflictException(
                        "A class-teacher assignment cannot contain a subject"
                );
            }

            if (assignmentRepository
                    .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndAssignmentType(
                            organizationId,
                            academicYearId,
                            gradeLevelId,
                            sectionId,
                            TeacherAssignmentType.CLASS_TEACHER
                    )) {
                throw new TeacherAssignmentConflictException(
                        "This section already has a class teacher"
                );
            }

            return;
        }

        if (request.gradeLevelSubjectId() == null) {
            throw new TeacherAssignmentConflictException(
                    "A subject-teacher assignment requires a grade subject"
            );
        }

        gradeSubjectRepository
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        request.gradeLevelSubjectId(),
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new GradeLevelSubjectNotFoundException(
                                "Grade-subject assignment not found"
                        )
                );

        if (assignmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectId(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        request.gradeLevelSubjectId()
                )) {
            throw new TeacherAssignmentConflictException(
                    "This section already has a teacher for this subject"
            );
        }
    }

    private void validateTeacherEligibility(
            long organizationId,
            long teacherUserId
    ) {
        TeachingEligibilityResponse response =
                eligibilityClient.check(
                        organizationId,
                        teacherUserId
                );

        boolean validResponse =
                response.userId() != null
                && response.organizationId() != null
                && response.userId().longValue() == teacherUserId
                && response.organizationId().longValue() == organizationId;

        if (!validResponse) {
            throw new TeacherNotEligibleException(
                    "Identity-service returned inconsistent teacher information"
            );
        }

        if (!response.eligible()
                || response.reason()
                != TeachingEligibilityReason.ELIGIBLE) {
            throw new TeacherNotEligibleException(
                    eligibilityMessage(response.reason())
            );
        }
    }

    private String eligibilityMessage(
            TeachingEligibilityReason reason
    ) {
        if (reason == null) {
            return "The selected user is not eligible to teach";
        }

        return switch (reason) {
            case USER_NOT_FOUND ->
                    "The selected teacher was not found in this organization";
            case USER_NOT_ACTIVE ->
                    "The selected teacher account is not active";
            case TEACHER_ROLE_REQUIRED ->
                    "The selected user does not have the teacher role";
            case ELIGIBLE ->
                    "The selected user is not eligible to teach";
        };
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

    private void findSection(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    ) {
        sectionRepository
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        sectionId,
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new SectionNotFoundException(
                                "Section not found"
                        )
                );
    }

    private TeacherAssignment findAssignment(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long assignmentId
    ) {
        return assignmentRepository
                .findByIdAndSectionIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        assignmentId,
                        sectionId,
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new TeacherAssignmentNotFoundException(
                                "Teacher assignment not found"
                        )
                );
    }

    private void ensureAcademicYearIsModifiable(
            AcademicYear academicYear
    ) {
        if (academicYear.getStatus() != AcademicYearStatus.PLANNED
                && academicYear.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new TeacherAssignmentConflictException(
                    "Teacher assignments can only be modified for a planned or active academic year"
            );
        }
    }
}