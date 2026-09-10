package com.dawnrise.academic.studentprogression.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityClient;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionItem;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionItemRepository;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanningData;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanningDataLoader;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StudentProgressionPlanningDataLoaderImpl
        implements StudentProgressionPlanningDataLoader {

    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SectionRepository sectionRepository;
    private final GradeLevelSubjectRepository gradeLevelSubjectRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentProgressionItemRepository progressionItemRepository;
    private final IdentityStudentEligibilityClient eligibilityClient;

    public StudentProgressionPlanningDataLoaderImpl(
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SectionRepository sectionRepository,
            GradeLevelSubjectRepository gradeLevelSubjectRepository,
            StudentEnrollmentRepository enrollmentRepository,
            StudentProgressionItemRepository progressionItemRepository,
            IdentityStudentEligibilityClient eligibilityClient
    ) {
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.sectionRepository = sectionRepository;
        this.gradeLevelSubjectRepository =
                gradeLevelSubjectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressionItemRepository =
                progressionItemRepository;
        this.eligibilityClient = eligibilityClient;
    }

    @Override
    public StudentProgressionPlanningData loadForPreview(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            List<StudentProgressionDecisionRequest> decisions
    ) {
        AcademicYear sourceAcademicYear =
                findAcademicYear(
                        organizationId,
                        sourceAcademicYearId
                );

        AcademicYear targetAcademicYear =
                findAcademicYear(
                        organizationId,
                        targetAcademicYearId
                );

        return loadRemainingData(
                organizationId,
                sourceAcademicYear,
                targetAcademicYear,
                decisions,
                false
        );
    }

    @Override
    public StudentProgressionPlanningData loadForConfirmation(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            List<StudentProgressionDecisionRequest> decisions
    ) {
        AcademicYears academicYears = lockAcademicYears(
                organizationId,
                sourceAcademicYearId,
                targetAcademicYearId
        );

        return loadRemainingData(
                organizationId,
                academicYears.source(),
                academicYears.target(),
                decisions,
                true
        );
    }

    private StudentProgressionPlanningData loadRemainingData(
            long organizationId,
            AcademicYear sourceAcademicYear,
            AcademicYear targetAcademicYear,
            List<StudentProgressionDecisionRequest> decisions,
            boolean lockSourceEnrollments
    ) {
        List<StudentProgressionDecisionRequest> safeDecisions =
                decisions == null ? List.of() : decisions;

        List<Long> sourceEnrollmentIds = safeDecisions.stream()
                .map(StudentProgressionDecisionRequest::sourceEnrollmentId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<StudentEnrollment> sourceEnrollments =
                loadSourceEnrollments(
                        organizationId,
                        sourceAcademicYear.getId(),
                        sourceEnrollmentIds,
                        lockSourceEnrollments
                );

        List<StudentProgressionItem> existingProgressionItems =
                sourceEnrollmentIds.isEmpty()
                        ? List.of()
                        : progressionItemRepository
                        .findAllByOrganizationIdAndSourceEnrollmentIdIn(
                                organizationId,
                                sourceEnrollmentIds
                        );

        Set<Long> sourceGradeLevelIds =
                sourceEnrollments.stream()
                        .map(StudentEnrollment::getGradeLevelId)
                        .collect(java.util.stream.Collectors.toCollection(
                                LinkedHashSet::new
                        ));

        List<GradeLevel> sourceGradeLevels =
                sourceGradeLevelIds.isEmpty()
                        ? List.of()
                        : gradeLevelRepository
                        .findAllByOrganizationIdAndAcademicYearIdAndIdIn(
                                organizationId,
                                sourceAcademicYear.getId(),
                                sourceGradeLevelIds
                        );

        List<Section> sourceSections =
                sourceGradeLevelIds.isEmpty()
                        ? List.of()
                        : sectionRepository
                        .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdInOrderByGradeLevelIdAscDisplayOrderAscIdAsc(
                                organizationId,
                                sourceAcademicYear.getId(),
                                sourceGradeLevelIds
                        );

        List<GradeLevel> targetGradeLevels =
                gradeLevelRepository
                        .findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc(
                                organizationId,
                                targetAcademicYear.getId()
                        );

        List<Section> targetSections =
                sectionRepository
                        .findAllByOrganizationIdAndAcademicYearIdOrderByGradeLevelIdAscDisplayOrderAscIdAsc(
                                organizationId,
                                targetAcademicYear.getId()
                        );

        List<GradeLevelSubject> targetGradeLevelSubjects =
                gradeLevelSubjectRepository
                        .findAllByOrganizationIdAndAcademicYearIdOrderByGradeLevelIdAscDisplayOrderAscIdAsc(
                                organizationId,
                                targetAcademicYear.getId()
                        );

        Set<Long> eligibleStudentUserIds =
                resolveEligibleStudentUserIds(
                        safeDecisions,
                        sourceEnrollments
                );

        List<StudentEnrollmentEligibilityResponse>
                eligibilityResponses =
                eligibleStudentUserIds.isEmpty()
                        ? List.of()
                        : eligibilityClient.checkBatch(
                        organizationId,
                        eligibleStudentUserIds.stream()
                                .sorted()
                                .toList()
                ).results();

        Set<Long> requestedTargetSectionIds =
                safeDecisions.stream()
                        .filter(this::createsTargetEnrollment)
                        .map(StudentProgressionDecisionRequest
                                ::targetSectionId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(
                                LinkedHashSet::new
                        ));

        List<StudentEnrollment> existingTargetEnrollments =
                loadExistingTargetEnrollments(
                        organizationId,
                        targetAcademicYear.getId(),
                        eligibleStudentUserIds,
                        requestedTargetSectionIds
                );

        return new StudentProgressionPlanningData(
                sourceAcademicYear,
                targetAcademicYear,
                sourceEnrollments,
                existingProgressionItems,
                sourceGradeLevels,
                sourceSections,
                targetGradeLevels,
                targetSections,
                targetGradeLevelSubjects,
                existingTargetEnrollments,
                eligibilityResponses
        );
    }

    private List<StudentEnrollment> loadSourceEnrollments(
            long organizationId,
            long sourceAcademicYearId,
            List<Long> sourceEnrollmentIds,
            boolean lockSourceEnrollments
    ) {
        if (sourceEnrollmentIds.isEmpty()) {
            return List.of();
        }

        if (lockSourceEnrollments) {
            return enrollmentRepository
                    .findAllByOrganizationIdAndAcademicYearIdAndIdInForUpdate(
                            organizationId,
                            sourceAcademicYearId,
                            sourceEnrollmentIds
                    );
        }

        return enrollmentRepository
                .findAllByOrganizationIdAndAcademicYearIdAndIdIn(
                        organizationId,
                        sourceAcademicYearId,
                        sourceEnrollmentIds
                );
    }

    private Set<Long> resolveEligibleStudentUserIds(
            List<StudentProgressionDecisionRequest> decisions,
            List<StudentEnrollment> sourceEnrollments
    ) {
        Map<Long, StudentEnrollment> enrollmentsById =
                sourceEnrollments.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                StudentEnrollment::getId,
                                enrollment -> enrollment
                        ));

        Set<Long> studentUserIds = new LinkedHashSet<>();

        for (StudentProgressionDecisionRequest decision : decisions) {
            if (!createsTargetEnrollment(decision)) {
                continue;
            }

            StudentEnrollment sourceEnrollment =
                    enrollmentsById.get(
                            decision.sourceEnrollmentId()
                    );

            if (sourceEnrollment != null) {
                studentUserIds.add(
                        sourceEnrollment.getStudentUserId()
                );
            }
        }

        return studentUserIds;
    }

    private List<StudentEnrollment> loadExistingTargetEnrollments(
            long organizationId,
            long targetAcademicYearId,
            Collection<Long> studentUserIds,
            Collection<Long> sectionIds
    ) {
        Map<Long, StudentEnrollment> enrollmentsById =
                new LinkedHashMap<>();

        if (!studentUserIds.isEmpty()) {
            enrollmentRepository
                    .findAllByOrganizationIdAndAcademicYearIdAndStudentUserIdInAndStatus(
                            organizationId,
                            targetAcademicYearId,
                            studentUserIds,
                            StudentEnrollmentStatus.ENROLLED
                    )
                    .forEach(enrollment ->
                            enrollmentsById.put(
                                    enrollment.getId(),
                                    enrollment
                            )
                    );
        }

        if (!sectionIds.isEmpty()) {
            enrollmentRepository
                    .findAllByOrganizationIdAndAcademicYearIdAndSectionIdInAndStatus(
                            organizationId,
                            targetAcademicYearId,
                            sectionIds,
                            StudentEnrollmentStatus.ENROLLED
                    )
                    .forEach(enrollment ->
                            enrollmentsById.put(
                                    enrollment.getId(),
                                    enrollment
                            )
                    );
        }

        return List.copyOf(enrollmentsById.values());
    }

    private boolean createsTargetEnrollment(
            StudentProgressionDecisionRequest decision
    ) {
        return decision.outcome()
                == StudentProgressionOutcome.PROMOTED
                || decision.outcome()
                == StudentProgressionOutcome.REPEATED;
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

    private AcademicYears lockAcademicYears(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId
    ) {
        long firstId = Math.min(
                sourceAcademicYearId,
                targetAcademicYearId
        );

        long secondId = Math.max(
                sourceAcademicYearId,
                targetAcademicYearId
        );

        AcademicYear first = lockAcademicYear(
                organizationId,
                firstId
        );

        AcademicYear second = lockAcademicYear(
                organizationId,
                secondId
        );

        AcademicYear source =
                first.getId().equals(sourceAcademicYearId)
                        ? first
                        : second;

        AcademicYear target =
                first.getId().equals(targetAcademicYearId)
                        ? first
                        : second;

        return new AcademicYears(source, target);
    }

    private AcademicYear lockAcademicYear(
            long organizationId,
            long academicYearId
    ) {
        return academicYearRepository
                .findByIdAndOrganizationIdForUpdate(
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new AcademicYearNotFoundException(
                                "Academic year not found"
                        )
                );
    }

    private record AcademicYears(
            AcademicYear source,
            AcademicYear target
    ) {
    }
}