package com.dawnrise.academic.studentenrollment.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.section.exception.SectionNotFoundException;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentenrollment.dto.*;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;
import com.dawnrise.academic.studentenrollment.exception.StudentEnrollmentConflictException;
import com.dawnrise.academic.studentenrollment.exception.StudentEnrollmentNotFoundException;
import com.dawnrise.academic.studentenrollment.exception.StudentNotEligibleException;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityClient;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityReason;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentenrollment.mapper.StudentEnrollmentMapper;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.studentenrollment.service.StudentEnrollmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class StudentEnrollmentServiceImpl
        implements StudentEnrollmentService {

    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SectionRepository sectionRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final IdentityStudentEligibilityClient eligibilityClient;
    private final StudentEnrollmentMapper enrollmentMapper;

    public StudentEnrollmentServiceImpl(
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SectionRepository sectionRepository,
            StudentEnrollmentRepository enrollmentRepository,
            IdentityStudentEligibilityClient eligibilityClient,
            StudentEnrollmentMapper enrollmentMapper
    ) {
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.sectionRepository = sectionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.eligibilityClient = eligibilityClient;
        this.enrollmentMapper = enrollmentMapper;
    }

    @Override
    public StudentEnrollmentResponse enroll(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            CreateStudentEnrollmentRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        validateDateWithinAcademicYear(
                academicYear,
                request.enrolledOn(),
                "Enrollment date"
        );

        validateHierarchy(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );

        validateStudentEligibility(
                organizationId,
                request.studentUserId()
        );

        if (enrollmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndStudentUserIdAndStatus(
                        organizationId,
                        academicYearId,
                        request.studentUserId(),
                        StudentEnrollmentStatus.ENROLLED
                )) {
            throw new StudentEnrollmentConflictException(
                    "Student already has an active enrollment for this academic year"
            );
        }

        StudentEnrollment enrollment = new StudentEnrollment(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                request.studentUserId(),
                request.rollNumber(),
                request.enrolledOn()
        );

        ensureRollNumberAvailable(
                organizationId,
                academicYearId,
                sectionId,
                enrollment.getRollNumber()
        );

        StudentEnrollment savedEnrollment =
                enrollmentRepository.saveAndFlush(enrollment);

        return enrollmentMapper.toResponse(savedEnrollment);
    }

    @Override
    public List<StudentEnrollmentResponse> enrollBulk(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            BulkCreateStudentEnrollmentsRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        validateHierarchy(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );
        validateBulkEnrollmentRequest(request);

        List<Long> studentUserIds = request.enrollments()
                .stream()
                .map(CreateStudentEnrollmentRequest::studentUserId)
                .toList();

        validateStudentEligibilityBatch(
                organizationId,
                studentUserIds
        );

        List<StudentEnrollment> enrollments = new ArrayList<>();
        for (CreateStudentEnrollmentRequest item : request.enrollments()) {
            validateDateWithinAcademicYear(
                    academicYear,
                    item.enrolledOn(),
                    "Enrollment date"
            );

            if (enrollmentRepository
                    .existsByOrganizationIdAndAcademicYearIdAndStudentUserIdAndStatus(
                            organizationId,
                            academicYearId,
                            item.studentUserId(),
                            StudentEnrollmentStatus.ENROLLED
                    )) {
                throw new StudentEnrollmentConflictException(
                        "Student already has an active enrollment for this academic year"
                );
            }

            StudentEnrollment enrollment = new StudentEnrollment(
                    organizationId,
                    academicYearId,
                    gradeLevelId,
                    sectionId,
                    item.studentUserId(),
                    item.rollNumber(),
                    item.enrolledOn()
            );

            ensureRollNumberAvailable(
                    organizationId,
                    academicYearId,
                    sectionId,
                    enrollment.getRollNumber()
            );
            enrollments.add(enrollment);
        }

        return enrollmentRepository.saveAllAndFlush(enrollments)
                .stream()
                .map(enrollmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentEnrollmentResponse getById(
            long organizationId,
            long academicYearId,
            long enrollmentId
    ) {
        return enrollmentMapper.toResponse(
                findEnrollment(
                        organizationId,
                        academicYearId,
                        enrollmentId
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentEnrollmentResponse> getSectionStudents(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    ) {
        findAcademicYear(organizationId, academicYearId);

        validateHierarchy(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );

        return enrollmentRepository
                .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndStatusOrderByRollNumberAsc(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        StudentEnrollmentStatus.ENROLLED
                )
                .stream()
                .map(enrollmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentEnrollmentResponse> getStudentHistory(
            long organizationId,
            long studentUserId
    ) {
        return enrollmentRepository
                .findAllByOrganizationIdAndStudentUserIdOrderByAcademicYearIdDescEnrolledOnDesc(
                        organizationId,
                        studentUserId
                )
                .stream()
                .map(enrollmentMapper::toResponse)
                .toList();
    }

    @Override
    public StudentEnrollmentResponse updateRollNumber(
            long organizationId,
            long academicYearId,
            long enrollmentId,
            UpdateStudentRollNumberRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);

        StudentEnrollment enrollment = findEnrollment(
                organizationId,
                academicYearId,
                enrollmentId
        );

        ensureVersion(enrollment, request.version());

        enrollment.updateRollNumber(request.rollNumber());

        if (enrollmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndSectionIdAndRollNumberIgnoreCaseAndStatusAndIdNot(
                        organizationId,
                        academicYearId,
                        enrollment.getSectionId(),
                        enrollment.getRollNumber(),
                        StudentEnrollmentStatus.ENROLLED,
                        enrollmentId
                )) {
            throw new StudentEnrollmentConflictException(
                    "This roll number is already used in the section"
            );
        }

        StudentEnrollment savedEnrollment =
                enrollmentRepository.saveAndFlush(enrollment);

        return enrollmentMapper.toResponse(savedEnrollment);
    }

    @Override
    public StudentTransferResponse transfer(
            long organizationId,
            long academicYearId,
            long enrollmentId,
            TransferStudentEnrollmentRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        validateDateWithinAcademicYear(
                academicYear,
                request.transferOn(),
                "Transfer date"
        );

        StudentEnrollment currentEnrollment = findEnrollment(
                organizationId,
                academicYearId,
                enrollmentId
        );

        ensureVersion(currentEnrollment, request.version());

        if (currentEnrollment.getGradeLevelId()
                .equals(request.destinationGradeLevelId())
                && currentEnrollment.getSectionId()
                .equals(request.destinationSectionId())) {
            throw new StudentEnrollmentConflictException(
                    "Destination enrollment target must differ from the current enrollment"
            );
        }

        validateHierarchy(
                organizationId,
                academicYearId,
                request.destinationGradeLevelId(),
                request.destinationSectionId()
        );

        StudentEnrollment newEnrollment = new StudentEnrollment(
                organizationId,
                academicYearId,
                request.destinationGradeLevelId(),
                request.destinationSectionId(),
                currentEnrollment.getStudentUserId(),
                request.newRollNumber(),
                request.transferOn()
        );

        ensureRollNumberAvailable(
                organizationId,
                academicYearId,
                request.destinationSectionId(),
                newEnrollment.getRollNumber()
        );

        /*
         * Flush the old status first so the partial unique index no
         * longer sees it as the student's active enrollment.
         */
        currentEnrollment.markTransferred(request.transferOn());

        StudentEnrollment transferredEnrollment =
                enrollmentRepository.saveAndFlush(currentEnrollment);

        StudentEnrollment savedNewEnrollment =
                enrollmentRepository.saveAndFlush(newEnrollment);

        return new StudentTransferResponse(
                enrollmentMapper.toResponse(transferredEnrollment),
                enrollmentMapper.toResponse(savedNewEnrollment)
        );
    }

    @Override
    public StudentEnrollmentResponse withdraw(
            long organizationId,
            long academicYearId,
            long enrollmentId,
            EndStudentEnrollmentRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        validateDateWithinAcademicYear(
                academicYear,
                request.endedOn(),
                "Withdrawal date"
        );

        StudentEnrollment enrollment = findEnrollment(
                organizationId,
                academicYearId,
                enrollmentId
        );

        ensureVersion(enrollment, request.version());
        enrollment.markWithdrawn(request.endedOn());

        return enrollmentMapper.toResponse(
                enrollmentRepository.saveAndFlush(enrollment)
        );
    }

    @Override
    public StudentEnrollmentResponse complete(
            long organizationId,
            long academicYearId,
            long enrollmentId,
            EndStudentEnrollmentRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);
        validateDateWithinAcademicYear(
                academicYear,
                request.endedOn(),
                "Completion date"
        );

        StudentEnrollment enrollment = findEnrollment(
                organizationId,
                academicYearId,
                enrollmentId
        );

        ensureVersion(enrollment, request.version());
        enrollment.markCompleted(request.endedOn());

        return enrollmentMapper.toResponse(
                enrollmentRepository.saveAndFlush(enrollment)
        );
    }

    private void validateStudentEligibility(
            long organizationId,
            long studentUserId
    ) {
        StudentEnrollmentEligibilityResponse response =
                eligibilityClient.check(
                        organizationId,
                        studentUserId
                );

        boolean consistentResponse =
                response != null
                        && response.userId() != null
                        && response.organizationId() != null
                        && response.userId().longValue() == studentUserId
                        && response.organizationId().longValue()
                        == organizationId;

        if (!consistentResponse) {
            throw new StudentNotEligibleException(
                    "Identity-service returned inconsistent student information"
            );
        }

        if (!response.eligible()
                || response.reason()
                != StudentEnrollmentEligibilityReason.ELIGIBLE) {
            throw new StudentNotEligibleException(
                    eligibilityMessage(response.reason())
            );
        }
    }

    private void validateStudentEligibilityBatch(
            long organizationId,
            List<Long> studentUserIds
    ) {
        List<StudentEnrollmentEligibilityResponse> results =
                eligibilityClient.checkBatch(
                                organizationId,
                                studentUserIds
                        )
                        .results();

        if (results.size() != studentUserIds.size()) {
            throw new StudentNotEligibleException(
                    "Identity-service returned incomplete student information"
            );
        }

        Map<Long, StudentEnrollmentEligibilityResponse> resultsByUserId =
                results.stream()
                        .collect(Collectors.toMap(
                                StudentEnrollmentEligibilityResponse::userId,
                                Function.identity()
                        ));

        for (Long studentUserId : studentUserIds) {
            StudentEnrollmentEligibilityResponse response =
                    resultsByUserId.get(studentUserId);

            boolean consistentResponse =
                    response != null
                            && response.organizationId() != null
                            && response.userId() != null
                            && response.organizationId().longValue()
                            == organizationId;

            if (!consistentResponse
                    || !response.eligible()
                    || response.reason()
                    != StudentEnrollmentEligibilityReason.ELIGIBLE) {
                throw new StudentNotEligibleException(
                        eligibilityMessage(
                                response == null ? null : response.reason()
                        )
                );
            }
        }
    }

    private void validateBulkEnrollmentRequest(
            BulkCreateStudentEnrollmentsRequest request
    ) {
        Set<Long> studentUserIds = new HashSet<>();
        Set<String> rollNumbers = new HashSet<>();

        for (CreateStudentEnrollmentRequest item : request.enrollments()) {
            if (!studentUserIds.add(item.studentUserId())) {
                throw new StudentEnrollmentConflictException(
                        "Duplicate student IDs are not allowed"
                );
            }

            String normalizedRollNumber =
                    item.rollNumber().trim().toUpperCase(Locale.ROOT);
            if (!rollNumbers.add(normalizedRollNumber)) {
                throw new StudentEnrollmentConflictException(
                        "Duplicate roll numbers are not allowed"
                );
            }
        }
    }

    private String eligibilityMessage(
            StudentEnrollmentEligibilityReason reason
    ) {
        if (reason == null) {
            return "The selected user is not eligible for enrollment";
        }

        return switch (reason) {
            case USER_NOT_FOUND ->
                    "The selected student was not found in this organization";
            case USER_NOT_ENROLLABLE ->
                    "The selected student account is not eligible for enrollment";
            case STUDENT_ROLE_REQUIRED ->
                    "The selected user does not have the student role";
            case ELIGIBLE ->
                    "The selected user is not eligible for enrollment";
        };
    }

    private void ensureRollNumberAvailable(
            long organizationId,
            long academicYearId,
            long sectionId,
            String rollNumber
    ) {
        if (enrollmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndSectionIdAndRollNumberIgnoreCaseAndStatus(
                        organizationId,
                        academicYearId,
                        sectionId,
                        rollNumber,
                        StudentEnrollmentStatus.ENROLLED
                )) {
            throw new StudentEnrollmentConflictException(
                    "This roll number is already used in the section"
            );
        }
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

    private void validateHierarchy(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
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

    private StudentEnrollment findEnrollment(
            long organizationId,
            long academicYearId,
            long enrollmentId
    ) {
        return enrollmentRepository
                .findByIdAndAcademicYearIdAndOrganizationId(
                        enrollmentId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new StudentEnrollmentNotFoundException(
                                "Student enrollment not found"
                        )
                );
    }

    private void ensureVersion(
            StudentEnrollment enrollment,
            Long requestedVersion
    ) {
        if (!Objects.equals(
                enrollment.getVersion(),
                requestedVersion
        )) {
            throw new StudentEnrollmentConflictException(
                    "Student enrollment was modified by another request"
            );
        }
    }

    private void validateDateWithinAcademicYear(
            AcademicYear academicYear,
            LocalDate date,
            String fieldName
    ) {
        if (date == null) {
            return;
        }

        if (date.isBefore(academicYear.getStartDate())
                || date.isAfter(academicYear.getEndDate())) {
            throw new StudentEnrollmentConflictException(
                    fieldName
                            + " must be within the academic year"
            );
        }
    }

    private void ensureAcademicYearIsModifiable(
            AcademicYear academicYear
    ) {
        if (academicYear.getStatus() != AcademicYearStatus.PLANNED
                && academicYear.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new StudentEnrollmentConflictException(
                    "Enrollments can only be modified for a planned or active academic year"
            );
        }
    }
}
