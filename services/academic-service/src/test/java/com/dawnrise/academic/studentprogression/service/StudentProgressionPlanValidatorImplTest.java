package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityReason;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionItem;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import com.dawnrise.academic.studentprogression.service.impl.StudentProgressionPlanValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static com.dawnrise.academic.studentprogression.service.StudentProgressionConflictCodes.*;
import static org.assertj.core.api.Assertions.assertThat;

class StudentProgressionPlanValidatorImplTest {

    private StudentProgressionPlanValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new StudentProgressionPlanValidatorImpl();
    }

    @Test
    void validPromotionCanBeConfirmed() {
        StudentProgressionPlan plan = validator.validate(
                validData(),
                "  Annual promotion  ",
                List.of(promote(100L, 201L, 301L, "A-1")),
                true
        );

        assertThat(plan.batchLabel()).isEqualTo("Annual promotion");
        assertThat(plan.canConfirm()).isTrue();
        assertThat(plan.conflicts()).isEmpty();
        assertThat(plan.items()).hasSize(1);
        assertThat(plan.items().getFirst().conflicts()).isEmpty();
        assertThat(plan.items().getFirst().suggestedTargetGradeLevelId())
                .isEqualTo(201L);
        assertThat(plan.items().getFirst().suggestedTargetSectionId())
                .isEqualTo(301L);
    }

    @Test
    void previewAllowsActiveSourceButConfirmationRequiresClosedSource() {
        StudentProgressionPlanningData data =
                validData(sourceYear(AcademicYearStatus.ACTIVE));

        StudentProgressionPlan previewPlan = validator.validate(
                data,
                "Annual promotion",
                List.of(promote(100L, 201L, 301L, "A-1")),
                false
        );

        StudentProgressionPlan confirmationPlan = validator.validate(
                data,
                "Annual promotion",
                List.of(promote(100L, 201L, 301L, "A-1")),
                true
        );

        assertThat(previewPlan.conflicts()).isEmpty();
        assertThat(conflictCodes(confirmationPlan))
                .contains(SOURCE_YEAR_INVALID_STATUS);
    }

    @Test
    void targetYearMustBeAfterSourceAndPlannedWithReadyStructure() {
        AcademicYear target = year(
                2L,
                AcademicYearStatus.ACTIVE,
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 12, 30)
        );

        StudentProgressionPlan plan = validator.validate(
                validData(target, List.of(), List.of(), List.of()),
                "Annual promotion",
                List.of(promote(100L, 201L, 301L, "A-1")),
                true
        );

        assertThat(conflictCodes(plan))
                .containsExactlyInAnyOrder(
                        TARGET_YEAR_NOT_PLANNED,
                        TARGET_YEAR_NOT_AFTER_SOURCE,
                        TARGET_STRUCTURE_NOT_READY
                );
    }

    @Test
    void duplicateSourceDecisionAndMissingSourceAreReportedPerItem() {
        StudentProgressionPlan plan = validator.validate(
                validData(),
                "Annual promotion",
                List.of(
                        promote(100L, 201L, 301L, "A-1"),
                        promote(100L, 201L, 301L, "A-2"),
                        promote(999L, 201L, 301L, "A-3")
                ),
                false
        );

        assertThat(itemConflictCodes(plan, 100L))
                .contains(DUPLICATE_SOURCE_DECISION);
        assertThat(itemConflictCodes(plan, 999L))
                .contains(SOURCE_ENROLLMENT_NOT_FOUND);
    }

    @Test
    void promotionRequiresHigherTargetGrade() {
        StudentProgressionPlan plan = validator.validate(
                validData(),
                "Annual promotion",
                List.of(promote(100L, 200L, 300L, "A-1")),
                false
        );

        assertThat(itemConflictCodes(plan, 100L))
                .contains(PROMOTED_TARGET_NOT_HIGHER);
    }

    @Test
    void repeatRequiresEquivalentTargetGrade() {
        StudentProgressionPlan plan = validator.validate(
                validData(),
                "Annual repeat",
                List.of(new StudentProgressionDecisionRequest(
                        100L,
                        StudentProgressionOutcome.REPEATED,
                        201L,
                        301L,
                        "A-1",
                        LocalDate.of(2025, 12, 31),
                        null
                )),
                false
        );

        assertThat(itemConflictCodes(plan, 100L))
                .contains(REPEATED_TARGET_NOT_EQUIVALENT);
    }

    @Test
    void rollNumberConflictsAreCaseInsensitiveAndTrimmed() {
        StudentEnrollment targetEnrollment = enrollment(
                900L,
                2L,
                201L,
                301L,
                9000L,
                " A-1 "
        );

        StudentProgressionPlan plan = validator.validate(
                validData(List.of(targetEnrollment)),
                "Annual promotion",
                List.of(promote(100L, 201L, 301L, "a-1")),
                false
        );

        assertThat(itemConflictCodes(plan, 100L))
                .contains(TARGET_ROLL_NUMBER_CONFLICT);
    }

    @Test
    void duplicateRequestedTargetRollNumbersAreReportedForBothItems() {
        StudentEnrollment secondSource = enrollment(
                101L,
                1L,
                200L,
                300L,
                1001L,
                "S-2"
        );

        StudentProgressionPlan plan = validator.validate(
                validData(
                        List.of(sourceEnrollment(), secondSource),
                        List.of(),
                        eligible(1000L),
                        eligible(1001L)
                ),
                "Annual promotion",
                List.of(
                        promote(100L, 201L, 301L, "A-1"),
                        promote(101L, 201L, 301L, " a-1 ")
                ),
                false
        );

        assertThat(itemConflictCodes(plan, 100L))
                .contains(DUPLICATE_TARGET_ROLL_NUMBER);
        assertThat(itemConflictCodes(plan, 101L))
                .contains(DUPLICATE_TARGET_ROLL_NUMBER);
    }

    @Test
    void nonEnrollmentOutcomesRejectTargetFieldsAndManualReviewBlocksConfirm() {
        StudentProgressionPlan graduated = validator.validate(
                validData(),
                "Graduation",
                List.of(new StudentProgressionDecisionRequest(
                        100L,
                        StudentProgressionOutcome.GRADUATED,
                        201L,
                        301L,
                        "A-1",
                        LocalDate.of(2025, 12, 31),
                        null
                )),
                true
        );

        StudentProgressionPlan manualReview = validator.validate(
                validData(),
                "Manual review",
                List.of(new StudentProgressionDecisionRequest(
                        100L,
                        StudentProgressionOutcome.MANUAL_REVIEW,
                        null,
                        null,
                        null,
                        LocalDate.of(2025, 12, 31),
                        null
                )),
                true
        );

        assertThat(itemConflictCodes(graduated, 100L))
                .contains(TARGET_FIELDS_NOT_ALLOWED);
        assertThat(itemConflictCodes(manualReview, 100L))
                .contains(MANUAL_REVIEW_REQUIRED);
        assertThat(manualReview.canConfirm()).isFalse();
    }

    @Test
    void ineligibleStudentAndOutOfRangeEffectiveDateAreBlockingConflicts() {
        StudentProgressionPlan plan = validator.validate(
                validData(
                        List.of(sourceEnrollment()),
                        List.of(),
                        new StudentEnrollmentEligibilityResponse(
                                1000L,
                                10L,
                                "Student",
                                false,
                                StudentEnrollmentEligibilityReason
                                        .USER_NOT_ENROLLABLE
                        )
                ),
                "Annual promotion",
                List.of(new StudentProgressionDecisionRequest(
                        100L,
                        StudentProgressionOutcome.PROMOTED,
                        201L,
                        301L,
                        "A-1",
                        LocalDate.of(2026, 1, 1),
                        null
                )),
                true
        );

        assertThat(itemConflictCodes(plan, 100L))
                .contains(
                        EFFECTIVE_DATE_OUTSIDE_SOURCE_YEAR,
                        STUDENT_NOT_ELIGIBLE
                );
        assertThat(plan.canConfirm()).isFalse();
    }

    private static List<String> conflictCodes(StudentProgressionPlan plan) {
        return plan.conflicts()
                .stream()
                .map(conflict -> conflict.code())
                .toList();
    }

    private static List<String> itemConflictCodes(
            StudentProgressionPlan plan,
            Long sourceEnrollmentId
    ) {
        return plan.items()
                .stream()
                .filter(item -> item.decision()
                        .sourceEnrollmentId()
                        .equals(sourceEnrollmentId))
                .flatMap(item -> item.conflicts().stream())
                .map(conflict -> conflict.code())
                .toList();
    }

    private static StudentProgressionDecisionRequest promote(
            Long sourceEnrollmentId,
            Long targetGradeLevelId,
            Long targetSectionId,
            String targetRollNumber
    ) {
        return new StudentProgressionDecisionRequest(
                sourceEnrollmentId,
                StudentProgressionOutcome.PROMOTED,
                targetGradeLevelId,
                targetSectionId,
                targetRollNumber,
                LocalDate.of(2025, 12, 31),
                null
        );
    }

    private static StudentProgressionPlanningData validData() {
        return validData(List.of());
    }

    private static StudentProgressionPlanningData validData(
            List<StudentEnrollment> existingTargetEnrollments
    ) {
        return validData(
                List.of(sourceEnrollment()),
                existingTargetEnrollments,
                eligible(1000L)
        );
    }

    private static StudentProgressionPlanningData validData(
            AcademicYear sourceAcademicYear
    ) {
        return new StudentProgressionPlanningData(
                sourceAcademicYear,
                targetYear(),
                List.of(sourceEnrollment()),
                List.of(),
                List.of(sourceGrade()),
                List.of(sourceSection()),
                targetGrades(),
                targetSections(),
                targetSubjects(),
                List.of(),
                List.of(eligible(1000L))
        );
    }

    private static StudentProgressionPlanningData validData(
            List<StudentEnrollment> sourceEnrollments,
            List<StudentEnrollment> existingTargetEnrollments,
            StudentEnrollmentEligibilityResponse... eligibilityResponses
    ) {
        return new StudentProgressionPlanningData(
                sourceYear(AcademicYearStatus.CLOSED),
                targetYear(),
                sourceEnrollments,
                List.of(),
                List.of(sourceGrade()),
                List.of(sourceSection()),
                targetGrades(),
                targetSections(),
                targetSubjects(),
                existingTargetEnrollments,
                List.of(eligibilityResponses)
        );
    }

    private static StudentProgressionPlanningData validData(
            AcademicYear targetAcademicYear,
            List<GradeLevel> targetGradeLevels,
            List<Section> targetSections,
            List<GradeLevelSubject> targetGradeLevelSubjects
    ) {
        return new StudentProgressionPlanningData(
                sourceYear(AcademicYearStatus.CLOSED),
                targetAcademicYear,
                List.of(sourceEnrollment()),
                List.of(),
                List.of(sourceGrade()),
                List.of(sourceSection()),
                targetGradeLevels,
                targetSections,
                targetGradeLevelSubjects,
                List.of(),
                List.of(eligible(1000L))
        );
    }

    private static AcademicYear sourceYear(AcademicYearStatus status) {
        return year(
                1L,
                status,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31)
        );
    }

    private static AcademicYear targetYear() {
        return year(
                2L,
                AcademicYearStatus.PLANNED,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
    }

    private static AcademicYear year(
            Long id,
            AcademicYearStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        AcademicYear year = new AcademicYear(
                10L,
                "Academic " + id,
                startDate,
                endDate
        );
        if (status == AcademicYearStatus.ACTIVE) {
            year.activate();
        } else if (status == AcademicYearStatus.CLOSED) {
            year.activate();
            year.close();
        } else if (status == AcademicYearStatus.VOIDED) {
            year.voidYear("test", 1L);
        }
        ReflectionTestUtils.setField(year, "id", id);
        return year;
    }

    private static StudentEnrollment sourceEnrollment() {
        return enrollment(100L, 1L, 200L, 300L, 1000L, "S-1");
    }

    private static StudentEnrollment enrollment(
            Long id,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            Long studentUserId,
            String rollNumber
    ) {
        StudentEnrollment enrollment = new StudentEnrollment(
                10L,
                academicYearId,
                gradeLevelId,
                sectionId,
                studentUserId,
                rollNumber,
                LocalDate.of(2025, 1, 1)
        );
        ReflectionTestUtils.setField(enrollment, "id", id);
        return enrollment;
    }

    private static GradeLevel sourceGrade() {
        return grade(200L, 1L, "G1", 1);
    }

    private static List<GradeLevel> targetGrades() {
        return List.of(
                grade(200L, 2L, "G1", 1),
                grade(201L, 2L, "G2", 2)
        );
    }

    private static GradeLevel grade(
            Long id,
            Long academicYearId,
            String code,
            int displayOrder
    ) {
        GradeLevel grade = new GradeLevel(
                10L,
                academicYearId,
                code,
                "Grade " + code,
                displayOrder
        );
        ReflectionTestUtils.setField(grade, "id", id);
        return grade;
    }

    private static Section sourceSection() {
        return section(300L, 1L, 200L, "A");
    }

    private static List<Section> targetSections() {
        return List.of(
                section(300L, 2L, 200L, "A"),
                section(301L, 2L, 201L, "A")
        );
    }

    private static Section section(
            Long id,
            Long academicYearId,
            Long gradeLevelId,
            String code
    ) {
        Section section = new Section(
                10L,
                academicYearId,
                gradeLevelId,
                code,
                "Section " + code,
                1
        );
        ReflectionTestUtils.setField(section, "id", id);
        return section;
    }

    private static List<GradeLevelSubject> targetSubjects() {
        return List.of(
                subject(1L, 200L),
                subject(2L, 201L)
        );
    }

    private static GradeLevelSubject subject(Long id, Long gradeLevelId) {
        GradeLevelSubject subject = new GradeLevelSubject(
                10L,
                2L,
                gradeLevelId,
                500L + id,
                true,
                1
        );
        ReflectionTestUtils.setField(subject, "id", id);
        return subject;
    }

    private static StudentEnrollmentEligibilityResponse eligible(Long userId) {
        return new StudentEnrollmentEligibilityResponse(
                userId,
                10L,
                "Student " + userId,
                true,
                StudentEnrollmentEligibilityReason.ELIGIBLE
        );
    }
}
