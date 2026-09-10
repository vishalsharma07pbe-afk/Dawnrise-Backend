package com.dawnrise.academic.studentprogression.service.impl;

import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityReason;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConflictResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionItem;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlan;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanItem;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanValidator;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanningData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.dawnrise.academic.studentprogression.service.StudentProgressionConflictCodes.*;

@Component
public class StudentProgressionPlanValidatorImpl
        implements StudentProgressionPlanValidator {

    @Override
    public StudentProgressionPlan validate(
            StudentProgressionPlanningData data,
            String batchLabel,
            List<StudentProgressionDecisionRequest> decisions,
            boolean confirmation
    ) {
        List<StudentProgressionConflictResponse> globalConflicts =
                validateOperation(data, confirmation);

        Map<Long, Long> decisionCounts = decisions.stream()
                .filter(decision ->
                        decision.sourceEnrollmentId() != null
                )
                .collect(Collectors.groupingBy(
                        StudentProgressionDecisionRequest
                                ::sourceEnrollmentId,
                        Collectors.counting()
                ));

        Map<Long, StudentEnrollment> sourceEnrollmentsById =
                mapById(
                        data.sourceEnrollments(),
                        StudentEnrollment::getId
                );

        Map<Long, GradeLevel> sourceGradesById =
                mapById(
                        data.sourceGradeLevels(),
                        GradeLevel::getId
                );

        Map<Long, Section> sourceSectionsById =
                mapById(
                        data.sourceSections(),
                        Section::getId
                );

        Map<Long, GradeLevel> targetGradesById =
                mapById(
                        data.targetGradeLevels(),
                        GradeLevel::getId
                );

        Map<Long, Section> targetSectionsById =
                mapById(
                        data.targetSections(),
                        Section::getId
                );

        Set<Long> targetGradesWithSubjects =
                data.targetGradeLevelSubjects()
                        .stream()
                        .map(GradeLevelSubject::getGradeLevelId)
                        .collect(Collectors.toSet());

        Set<Long> progressedSourceIds =
                data.existingProgressionItems()
                        .stream()
                        .map(StudentProgressionItem
                                ::getSourceEnrollmentId)
                        .collect(Collectors.toSet());

        Map<Long, StudentEnrollment> targetEnrollmentByStudent =
                data.existingTargetEnrollments()
                        .stream()
                        .collect(Collectors.toMap(
                                StudentEnrollment::getStudentUserId,
                                Function.identity(),
                                (first, second) -> first
                        ));

        Set<TargetRollKey> occupiedTargetRolls =
                data.existingTargetEnrollments()
                        .stream()
                        .map(enrollment ->
                                new TargetRollKey(
                                        enrollment.getSectionId(),
                                        normalizeRollNumber(
                                                enrollment.getRollNumber()
                                        )
                                )
                        )
                        .collect(Collectors.toSet());

        Map<TargetRollKey, Long> requestedRollCounts =
                decisions.stream()
                        .filter(this::createsTargetEnrollment)
                        .filter(decision ->
                                decision.targetSectionId() != null
                                        && hasText(
                                        decision.targetRollNumber()
                                )
                        )
                        .collect(Collectors.groupingBy(
                                decision -> new TargetRollKey(
                                        decision.targetSectionId(),
                                        normalizeRollNumber(
                                                decision.targetRollNumber()
                                        )
                                ),
                                Collectors.counting()
                        ));

        Map<Long, StudentEnrollmentEligibilityResponse>
                eligibilityByStudent =
                data.eligibilityResponses()
                        .stream()
                        .filter(response ->
                                response != null
                                        && response.userId() != null
                        )
                        .collect(Collectors.toMap(
                                StudentEnrollmentEligibilityResponse
                                        ::userId,
                                Function.identity(),
                                (first, second) -> first
                        ));

        List<StudentProgressionPlanItem> planItems =
                new ArrayList<>();

        for (StudentProgressionDecisionRequest decision : decisions) {
            planItems.add(validateItem(
                    data,
                    decision,
                    decisionCounts,
                    sourceEnrollmentsById,
                    sourceGradesById,
                    sourceSectionsById,
                    targetGradesById,
                    targetSectionsById,
                    targetGradesWithSubjects,
                    progressedSourceIds,
                    targetEnrollmentByStudent,
                    occupiedTargetRolls,
                    requestedRollCounts,
                    eligibilityByStudent
            ));
        }

        return new StudentProgressionPlan(
                data.sourceAcademicYear(),
                data.targetAcademicYear(),
                batchLabel.trim(),
                planItems,
                globalConflicts,
                null
        );
    }

    private List<StudentProgressionConflictResponse>
    validateOperation(
            StudentProgressionPlanningData data,
            boolean confirmation
    ) {
        List<StudentProgressionConflictResponse> conflicts =
                new ArrayList<>();

        AcademicYearStatus sourceStatus =
                data.sourceAcademicYear().getStatus();

        boolean validSourceStatus = confirmation
                ? sourceStatus == AcademicYearStatus.CLOSED
                : sourceStatus == AcademicYearStatus.ACTIVE
                || sourceStatus == AcademicYearStatus.CLOSED;

        if (!validSourceStatus) {
            conflicts.add(conflict(
                    SOURCE_YEAR_INVALID_STATUS,
                    null,
                    "sourceAcademicYearId",
                    confirmation
                            ? "Confirmation requires a closed source academic year"
                            : "Preview requires an active or closed source academic year"
            ));
        }

        if (data.targetAcademicYear().getStatus()
                != AcademicYearStatus.PLANNED) {
            conflicts.add(conflict(
                    TARGET_YEAR_NOT_PLANNED,
                    null,
                    "targetAcademicYearId",
                    "Target academic year must be planned"
            ));
        }

        if (!data.targetAcademicYear().getStartDate()
                .isAfter(data.sourceAcademicYear().getEndDate())) {
            conflicts.add(conflict(
                    TARGET_YEAR_NOT_AFTER_SOURCE,
                    null,
                    "targetAcademicYearId",
                    "Target academic year must begin after the source academic year ends"
            ));
        }

        if (data.targetGradeLevels().isEmpty()
                || data.targetSections().isEmpty()
                || data.targetGradeLevelSubjects().isEmpty()) {
            conflicts.add(conflict(
                    TARGET_STRUCTURE_NOT_READY,
                    null,
                    "targetAcademicYearId",
                    "Target academic year must contain grades, sections, and grade-subject mappings"
            ));
        }

        return conflicts;
    }

    private StudentProgressionPlanItem validateItem(
            StudentProgressionPlanningData data,
            StudentProgressionDecisionRequest decision,
            Map<Long, Long> decisionCounts,
            Map<Long, StudentEnrollment> sourceEnrollmentsById,
            Map<Long, GradeLevel> sourceGradesById,
            Map<Long, Section> sourceSectionsById,
            Map<Long, GradeLevel> targetGradesById,
            Map<Long, Section> targetSectionsById,
            Set<Long> targetGradesWithSubjects,
            Set<Long> progressedSourceIds,
            Map<Long, StudentEnrollment> targetEnrollmentByStudent,
            Set<TargetRollKey> occupiedTargetRolls,
            Map<TargetRollKey, Long> requestedRollCounts,
            Map<Long, StudentEnrollmentEligibilityResponse>
                    eligibilityByStudent
    ) {
        Long sourceEnrollmentId = decision.sourceEnrollmentId();

        List<StudentProgressionConflictResponse> conflicts =
                new ArrayList<>();

        if (decisionCounts.getOrDefault(
                sourceEnrollmentId,
                0L
        ) > 1) {
            conflicts.add(conflict(
                    DUPLICATE_SOURCE_DECISION,
                    sourceEnrollmentId,
                    "sourceEnrollmentId",
                    "Source enrollment appears more than once in the request"
            ));
        }

        StudentEnrollment sourceEnrollment =
                sourceEnrollmentsById.get(sourceEnrollmentId);

        if (sourceEnrollment == null) {
            conflicts.add(conflict(
                    SOURCE_ENROLLMENT_NOT_FOUND,
                    sourceEnrollmentId,
                    "sourceEnrollmentId",
                    "Source enrollment was not found in the source academic year"
            ));

            return new StudentProgressionPlanItem(
                    decision,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    conflicts
            );
        }

        GradeLevel sourceGrade =
                sourceGradesById.get(
                        sourceEnrollment.getGradeLevelId()
                );

        Section sourceSection =
                sourceSectionsById.get(
                        sourceEnrollment.getSectionId()
                );

        if (sourceEnrollment.getStatus()
                != StudentEnrollmentStatus.ENROLLED) {
            conflicts.add(conflict(
                    SOURCE_ENROLLMENT_NOT_ACTIVE,
                    sourceEnrollmentId,
                    "sourceEnrollmentId",
                    "Only an active source enrollment can be progressed"
            ));
        }

        if (progressedSourceIds.contains(sourceEnrollmentId)) {
            conflicts.add(conflict(
                    SOURCE_ENROLLMENT_ALREADY_PROGRESSED,
                    sourceEnrollmentId,
                    "sourceEnrollmentId",
                    "Source enrollment has already been progressed"
            ));
        }

        if (decision.effectiveOn() != null
                && (decision.effectiveOn().isBefore(
                data.sourceAcademicYear().getStartDate()
        )
                || decision.effectiveOn().isAfter(
                data.sourceAcademicYear().getEndDate()
        ))) {
            conflicts.add(conflict(
                    EFFECTIVE_DATE_OUTSIDE_SOURCE_YEAR,
                    sourceEnrollmentId,
                    "effectiveOn",
                    "Effective date must be within the source academic year"
            ));
        }

        GradeLevel suggestedTargetGrade =
                suggestTargetGrade(
                        decision.outcome(),
                        sourceGrade,
                        data.targetGradeLevels()
                );

        Section suggestedTargetSection =
                suggestTargetSection(
                        sourceSection,
                        suggestedTargetGrade,
                        data.targetSections()
                );

        GradeLevel targetGrade = null;
        Section targetSection = null;

        if (decision.outcome()
                == StudentProgressionOutcome.MANUAL_REVIEW) {
            validateNoTargetFields(
                    decision,
                    sourceEnrollmentId,
                    conflicts
            );

            conflicts.add(conflict(
                    MANUAL_REVIEW_REQUIRED,
                    sourceEnrollmentId,
                    "outcome",
                    "Manual-review decisions must be resolved before confirmation"
            ));
        } else if (createsTargetEnrollment(decision)) {
            boolean targetFieldsPresent =
                    decision.targetGradeLevelId() != null
                            && decision.targetSectionId() != null
                            && hasText(decision.targetRollNumber());

            if (!targetFieldsPresent) {
                conflicts.add(conflict(
                        TARGET_FIELDS_REQUIRED,
                        sourceEnrollmentId,
                        "targetGradeLevelId",
                        "Target grade, section, and roll number are required"
                ));
            }

            targetGrade = targetGradesById.get(
                    decision.targetGradeLevelId()
            );

            targetSection = targetSectionsById.get(
                    decision.targetSectionId()
            );

            if (decision.targetGradeLevelId() != null
                    && targetGrade == null) {
                conflicts.add(conflict(
                        TARGET_GRADE_NOT_FOUND,
                        sourceEnrollmentId,
                        "targetGradeLevelId",
                        "Target grade was not found in the target academic year"
                ));
            }

            if (decision.targetSectionId() != null
                    && (targetSection == null
                    || !Objects.equals(
                    targetSection.getGradeLevelId(),
                    decision.targetGradeLevelId()
            ))) {
                conflicts.add(conflict(
                        TARGET_SECTION_NOT_FOUND,
                        sourceEnrollmentId,
                        "targetSectionId",
                        "Target section does not belong to the selected target grade"
                ));
            }

            if (targetGrade != null
                    && !targetGradesWithSubjects.contains(
                    targetGrade.getId()
            )) {
                conflicts.add(conflict(
                        TARGET_STRUCTURE_NOT_READY,
                        sourceEnrollmentId,
                        "targetGradeLevelId",
                        "Selected target grade has no subject mappings"
                ));
            }

            validateGradeMovement(
                    decision,
                    sourceEnrollmentId,
                    sourceGrade,
                    targetGrade,
                    conflicts
            );

            if (targetEnrollmentByStudent.containsKey(
                    sourceEnrollment.getStudentUserId()
            )) {
                conflicts.add(conflict(
                        TARGET_STUDENT_ALREADY_ENROLLED,
                        sourceEnrollmentId,
                        "sourceEnrollmentId",
                        "Student already has an active target-year enrollment"
                ));
            }

            validateTargetRollNumber(
                    decision,
                    sourceEnrollmentId,
                    occupiedTargetRolls,
                    requestedRollCounts,
                    conflicts
            );

            validateEligibility(
                    data,
                    sourceEnrollment,
                    eligibilityByStudent,
                    conflicts
            );
        } else {
            validateNoTargetFields(
                    decision,
                    sourceEnrollmentId,
                    conflicts
            );
        }

        return new StudentProgressionPlanItem(
                decision,
                sourceEnrollment,
                sourceGrade,
                sourceSection,
                targetGrade,
                targetSection,
                suggestedTargetGrade == null
                        ? null
                        : suggestedTargetGrade.getId(),
                suggestedTargetSection == null
                        ? null
                        : suggestedTargetSection.getId(),
                conflicts
        );
    }

    private void validateGradeMovement(
            StudentProgressionDecisionRequest decision,
            Long sourceEnrollmentId,
            GradeLevel sourceGrade,
            GradeLevel targetGrade,
            List<StudentProgressionConflictResponse> conflicts
    ) {
        if (sourceGrade == null || targetGrade == null) {
            return;
        }

        if (decision.outcome()
                == StudentProgressionOutcome.REPEATED
                && !sourceGrade.getCode().equalsIgnoreCase(
                targetGrade.getCode()
        )) {
            conflicts.add(conflict(
                    REPEATED_TARGET_NOT_EQUIVALENT,
                    sourceEnrollmentId,
                    "targetGradeLevelId",
                    "A repeated student must move to the equivalent logical grade"
            ));
        }

        if (decision.outcome()
                == StudentProgressionOutcome.PROMOTED
                && targetGrade.getDisplayOrder()
                <= sourceGrade.getDisplayOrder()) {
            conflicts.add(conflict(
                    PROMOTED_TARGET_NOT_HIGHER,
                    sourceEnrollmentId,
                    "targetGradeLevelId",
                    "A promoted student must move to a higher logical grade"
            ));
        }
    }

    private void validateTargetRollNumber(
            StudentProgressionDecisionRequest decision,
            Long sourceEnrollmentId,
            Set<TargetRollKey> occupiedTargetRolls,
            Map<TargetRollKey, Long> requestedRollCounts,
            List<StudentProgressionConflictResponse> conflicts
    ) {
        if (decision.targetSectionId() == null
                || !hasText(decision.targetRollNumber())) {
            return;
        }

        TargetRollKey key = new TargetRollKey(
                decision.targetSectionId(),
                normalizeRollNumber(decision.targetRollNumber())
        );

        if (occupiedTargetRolls.contains(key)) {
            conflicts.add(conflict(
                    TARGET_ROLL_NUMBER_CONFLICT,
                    sourceEnrollmentId,
                    "targetRollNumber",
                    "Target roll number is already assigned in the target section"
            ));
        }

        if (requestedRollCounts.getOrDefault(key, 0L) > 1) {
            conflicts.add(conflict(
                    DUPLICATE_TARGET_ROLL_NUMBER,
                    sourceEnrollmentId,
                    "targetRollNumber",
                    "Target roll number appears more than once for the target section"
            ));
        }
    }

    private void validateEligibility(
            StudentProgressionPlanningData data,
            StudentEnrollment sourceEnrollment,
            Map<Long, StudentEnrollmentEligibilityResponse>
                    eligibilityByStudent,
            List<StudentProgressionConflictResponse> conflicts
    ) {
        StudentEnrollmentEligibilityResponse response =
                eligibilityByStudent.get(
                        sourceEnrollment.getStudentUserId()
                );

        boolean valid = response != null
                && Objects.equals(
                response.userId(),
                sourceEnrollment.getStudentUserId()
        )
                && Objects.equals(
                response.organizationId(),
                data.sourceAcademicYear().getOrganizationId()
        )
                && response.eligible()
                && response.reason()
                == StudentEnrollmentEligibilityReason.ELIGIBLE;

        if (!valid) {
            conflicts.add(conflict(
                    STUDENT_NOT_ELIGIBLE,
                    sourceEnrollment.getId(),
                    "sourceEnrollmentId",
                    "Student account is not eligible for target-year enrollment"
            ));
        }
    }

    private void validateNoTargetFields(
            StudentProgressionDecisionRequest decision,
            Long sourceEnrollmentId,
            List<StudentProgressionConflictResponse> conflicts
    ) {
        if (decision.targetGradeLevelId() != null
                || decision.targetSectionId() != null
                || hasText(decision.targetRollNumber())) {
            conflicts.add(conflict(
                    TARGET_FIELDS_NOT_ALLOWED,
                    sourceEnrollmentId,
                    "targetGradeLevelId",
                    "Target fields are not allowed for this progression outcome"
            ));
        }
    }

    private GradeLevel suggestTargetGrade(
            StudentProgressionOutcome outcome,
            GradeLevel sourceGrade,
            List<GradeLevel> targetGrades
    ) {
        if (sourceGrade == null || outcome == null) {
            return null;
        }

        if (outcome == StudentProgressionOutcome.REPEATED) {
            return targetGrades.stream()
                    .filter(target ->
                            target.getCode().equalsIgnoreCase(
                                    sourceGrade.getCode()
                            )
                    )
                    .findFirst()
                    .orElse(null);
        }

        if (outcome == StudentProgressionOutcome.PROMOTED) {
            return targetGrades.stream()
                    .filter(target ->
                            target.getDisplayOrder()
                                    > sourceGrade.getDisplayOrder()
                    )
                    .min(Comparator.comparing(
                            GradeLevel::getDisplayOrder
                    ))
                    .orElse(null);
        }

        return null;
    }

    private Section suggestTargetSection(
            Section sourceSection,
            GradeLevel suggestedTargetGrade,
            List<Section> targetSections
    ) {
        if (sourceSection == null
                || suggestedTargetGrade == null) {
            return null;
        }

        return targetSections.stream()
                .filter(target ->
                        Objects.equals(
                                target.getGradeLevelId(),
                                suggestedTargetGrade.getId()
                        )
                )
                .filter(target ->
                        target.getCode().equalsIgnoreCase(
                                sourceSection.getCode()
                        )
                )
                .findFirst()
                .orElse(null);
    }

    private boolean createsTargetEnrollment(
            StudentProgressionDecisionRequest decision
    ) {
        return decision.outcome()
                == StudentProgressionOutcome.PROMOTED
                || decision.outcome()
                == StudentProgressionOutcome.REPEATED;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeRollNumber(String value) {
        return value == null
                ? null
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private StudentProgressionConflictResponse conflict(
            String code,
            Long sourceEnrollmentId,
            String field,
            String message
    ) {
        return new StudentProgressionConflictResponse(
                code,
                sourceEnrollmentId,
                field,
                message
        );
    }

    private <T> Map<Long, T> mapById(
            List<T> values,
            Function<T, Long> idExtractor
    ) {
        Map<Long, T> valuesById = new HashMap<>();

        for (T value : values) {
            valuesById.put(idExtractor.apply(value), value);
        }

        return valuesById;
    }

    private record TargetRollKey(
            Long sectionId,
            String rollNumber
    ) {
    }
}