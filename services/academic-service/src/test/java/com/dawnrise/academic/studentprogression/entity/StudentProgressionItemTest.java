package com.dawnrise.academic.studentprogression.entity;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import com.dawnrise.academic.studentprogression.exception.InvalidStudentProgressionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentProgressionItemTest {

    @Test
    void promotedAndRepeatedRequireTargetEnrollment() {
        assertInvalid(() -> item(StudentProgressionOutcome.PROMOTED, null, null),
                "Target enrollment ID must be greater than zero");
        assertInvalid(() -> item(StudentProgressionOutcome.REPEATED, null, null),
                "Target enrollment ID must be greater than zero");
    }

    @Test
    void graduatedAndLeftForbidTargetEnrollment() {
        assertInvalid(() -> item(StudentProgressionOutcome.GRADUATED, 200L, null),
                "A target enrollment is not allowed for GRADUATED");
        assertInvalid(() -> item(StudentProgressionOutcome.LEFT, 200L, null),
                "A target enrollment is not allowed for LEFT");
    }

    @Test
    void manualReviewCannotBePersisted() {
        assertInvalid(() -> item(StudentProgressionOutcome.MANUAL_REVIEW, null, null),
                "A manual-review decision cannot be persisted as a confirmed progression item");
    }

    @Test
    void rejectsInvalidIdsAndMissingEffectiveDate() {
        assertInvalid(() -> new StudentProgressionItem(
                0L, 10L, 100L, 1000L, StudentProgressionOutcome.GRADUATED,
                null, effectiveOn(), null
        ), "Operation ID must be greater than zero");
        assertInvalid(() -> new StudentProgressionItem(
                100L, 0L, 100L, 1000L, StudentProgressionOutcome.GRADUATED,
                null, effectiveOn(), null
        ), "Organization ID must be greater than zero");
        assertInvalid(() -> new StudentProgressionItem(
                100L, 10L, 0L, 1000L, StudentProgressionOutcome.GRADUATED,
                null, effectiveOn(), null
        ), "Source enrollment ID must be greater than zero");
        assertInvalid(() -> new StudentProgressionItem(
                100L, 10L, 100L, 0L, StudentProgressionOutcome.GRADUATED,
                null, effectiveOn(), null
        ), "Student user ID must be greater than zero");
        assertInvalid(() -> new StudentProgressionItem(
                100L, 10L, 100L, 1000L, null, null, effectiveOn(), null
        ), "Progression outcome is required");
        assertInvalid(() -> new StudentProgressionItem(
                100L, 10L, 100L, 1000L, StudentProgressionOutcome.GRADUATED,
                null, null, null
        ), "Progression effective date is required");
    }

    @Test
    void trimsNotesConvertsBlankNotesToNullAndRejectsLongNotes() {
        StudentProgressionItem trimmed =
                item(StudentProgressionOutcome.GRADUATED, null, "  done  ");
        StudentProgressionItem blank =
                item(StudentProgressionOutcome.LEFT, null, "   ");

        assertThat(trimmed.getNote()).isEqualTo("done");
        assertThat(blank.getNote()).isNull();
        assertInvalid(() -> item(
                StudentProgressionOutcome.GRADUATED,
                null,
                "A".repeat(501)
        ), "Progression note cannot exceed 500 characters");
    }

    @Test
    void validPromotedItemStoresTargetEnrollment() {
        StudentProgressionItem item =
                item(StudentProgressionOutcome.PROMOTED, 200L, "promoted");

        assertThat(item.getOutcome())
                .isEqualTo(StudentProgressionOutcome.PROMOTED);
        assertThat(item.getTargetEnrollmentId()).isEqualTo(200L);
        assertThat(item.getEffectiveOn()).isEqualTo(effectiveOn());
        assertThat(item.getNote()).isEqualTo("promoted");
    }

    private static StudentProgressionItem item(
            StudentProgressionOutcome outcome,
            Long targetEnrollmentId,
            String note
    ) {
        return new StudentProgressionItem(
                100L,
                10L,
                100L,
                1000L,
                outcome,
                targetEnrollmentId,
                effectiveOn(),
                note
        );
    }

    private static void assertInvalid(
            ThrowingRunnable runnable,
            String message
    ) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(InvalidStudentProgressionException.class)
                .hasMessage(message);
    }

    private static LocalDate effectiveOn() {
        return LocalDate.of(2025, 12, 31);
    }

    private interface ThrowingRunnable {
        void run();
    }
}
