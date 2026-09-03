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
import com.dawnrise.academic.teacherassignment.dto.BulkCreateTeacherAssignmentsRequest;
import com.dawnrise.academic.teacherassignment.dto.BulkTeacherAssignmentItemRequest;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    public List<TeacherAssignmentResponse> createBulk(
            long organizationId,
            long academicYearId,
            BulkCreateTeacherAssignmentsRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        validateBulkRequest(request);

        List<Long> teacherUserIds = request.assignments()
                .stream()
                .map(BulkTeacherAssignmentItemRequest::teacherUserId)
                .distinct()
                .toList();

        validateTeacherEligibilityBatch(
                organizationId,
                teacherUserIds
        );

        List<TeacherAssignment> assignments = new ArrayList<>();

        for (BulkTeacherAssignmentItemRequest item
                : request.assignments()) {
            findGradeLevel(
                    organizationId,
                    academicYearId,
                    item.gradeLevelId()
            );
            findSection(
                    organizationId,
                    academicYearId,
                    item.gradeLevelId(),
                    item.sectionId()
            );
            validateBulkAssignmentTarget(
                    organizationId,
                    academicYearId,
                    item
            );

            assignments.add(new TeacherAssignment(
                    organizationId,
                    academicYearId,
                    item.gradeLevelId(),
                    item.sectionId(),
                    item.gradeLevelSubjectId(),
                    item.teacherUserId(),
                    item.assignmentType()
            ));
        }

        return assignmentRepository.saveAllAndFlush(assignments)
                .stream()
                .map(assignmentMapper::toResponse)
                .toList();
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

    private void validateBulkAssignmentTarget(
            long organizationId,
            long academicYearId,
            BulkTeacherAssignmentItemRequest request
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
                            request.gradeLevelId(),
                            request.sectionId(),
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
                        request.gradeLevelId(),
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
                        request.gradeLevelId(),
                        request.sectionId(),
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

    private void validateTeacherEligibilityBatch(
            long organizationId,
            List<Long> teacherUserIds
    ) {
        List<TeachingEligibilityResponse> results =
                eligibilityClient.checkBatch(
                                organizationId,
                                teacherUserIds
                        )
                        .results();

        if (results == null
                || results.size() != teacherUserIds.size()) {
            throw new TeacherNotEligibleException(
                    "Identity-service returned incomplete teacher information"
            );
        }

        Map<Long, TeachingEligibilityResponse> resultsByUserId =
                new java.util.HashMap<>();

        for (TeachingEligibilityResponse response : results) {
            if (response == null || response.userId() == null) {
                throw new TeacherNotEligibleException(
                        "Identity-service returned incomplete teacher information"
                );
            }

            if (resultsByUserId.put(response.userId(), response)
                    != null) {
                throw new TeacherNotEligibleException(
                        "Identity-service returned duplicate teacher information"
                );
            }
        }

        for (Long teacherUserId : teacherUserIds) {
            TeachingEligibilityResponse response =
                    resultsByUserId.get(teacherUserId);

            boolean validResponse =
                    response != null
                            && response.organizationId() != null
                            && response.userId() != null
                            && response.organizationId().longValue()
                            == organizationId;

            if (!validResponse
                    || !response.eligible()
                    || response.reason()
                    != TeachingEligibilityReason.ELIGIBLE) {
                throw new TeacherNotEligibleException(
                        eligibilityMessage(
                                response == null ? null : response.reason()
                        )
                );
            }
        }
    }

    private void validateBulkRequest(
            BulkCreateTeacherAssignmentsRequest request
    ) {
        Set<LogicalAssignmentKey> logicalAssignments =
                new HashSet<>();

        for (BulkTeacherAssignmentItemRequest item
                : request.assignments()) {
            LogicalAssignmentKey key = LogicalAssignmentKey.from(item);

            if (!logicalAssignments.add(key)) {
                throw new TeacherAssignmentConflictException(
                        "Duplicate teacher assignments are not allowed"
                );
            }
        }
    }

    private record LogicalAssignmentKey(
            Long gradeLevelId,
            Long sectionId,
            TeacherAssignmentType assignmentType,
            Long gradeLevelSubjectId
    ) {
        private static LogicalAssignmentKey from(
                BulkTeacherAssignmentItemRequest item
        ) {
            return new LogicalAssignmentKey(
                    item.gradeLevelId(),
                    item.sectionId(),
                    item.assignmentType(),
                    item.gradeLevelSubjectId()
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
