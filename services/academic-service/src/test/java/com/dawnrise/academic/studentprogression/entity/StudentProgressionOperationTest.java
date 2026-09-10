package com.dawnrise.academic.studentprogression.entity;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.exception.InvalidStudentProgressionException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentProgressionOperationTest {

    @Test
    void validConstructionStartsPending() {
        StudentProgressionOperation operation = operation();

        assertThat(operation.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.PENDING);
        assertThat(operation.getOrganizationId()).isEqualTo(10L);
        assertThat(operation.getSourceAcademicYearId()).isEqualTo(1L);
        assertThat(operation.getTargetAcademicYearId()).isEqualTo(2L);
        assertThat(operation.getRequestedByUserId()).isEqualTo(42L);
        assertThat(operation.getTotalItems()).isEqualTo(1);
    }

    @Test
    void normalizesBatchLabelAndIdempotencyKey() {
        StudentProgressionOperation operation =
                new StudentProgressionOperation(
                        10L,
                        1L,
                        2L,
                        "  Annual promotion  ",
                        " key-1 ",
                        hash('a'),
                        hash('b'),
                        42L,
                        1
                );

        assertThat(operation.getBatchLabel())
                .isEqualTo("Annual promotion");
        assertThat(operation.getIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void rejectsInvalidIds() {
        assertInvalid(() -> new StudentProgressionOperation(
                0L, 1L, 2L, "Batch", "key", hash('a'), hash('b'), 42L, 1
        ), "Organization ID must be greater than zero");

        assertInvalid(() -> new StudentProgressionOperation(
                10L, 0L, 2L, "Batch", "key", hash('a'), hash('b'), 42L, 1
        ), "Source academic year ID must be greater than zero");

        assertInvalid(() -> new StudentProgressionOperation(
                10L, 1L, 0L, "Batch", "key", hash('a'), hash('b'), 42L, 1
        ), "Target academic year ID must be greater than zero");

        assertInvalid(() -> new StudentProgressionOperation(
                10L, 1L, 2L, "Batch", "key", hash('a'), hash('b'), 0L, 1
        ), "Requested-by user ID must be greater than zero");
    }

    @Test
    void rejectsEqualYearsMalformedHashesAndInvalidItemCounts() {
        assertInvalid(() -> new StudentProgressionOperation(
                10L, 1L, 1L, "Batch", "key", hash('a'), hash('b'), 42L, 1
        ), "Source and target academic years must be different");

        assertInvalid(() -> new StudentProgressionOperation(
                10L, 1L, 2L, "Batch", "key", "sha256:ABC", hash('b'), 42L, 1
        ), "Request hash must use the sha256:<64 lowercase hex characters> format");

        assertInvalid(() -> new StudentProgressionOperation(
                10L, 1L, 2L, "Batch", "key", hash('a'), "sha256:abc", 42L, 1
        ), "Preview fingerprint must use the sha256:<64 lowercase hex characters> format");

        assertInvalid(() -> new StudentProgressionOperation(
                10L, 1L, 2L, "Batch", "key", hash('a'), hash('b'), 42L, 0
        ), "Total items must be between 1 and 2000");

        assertInvalid(() -> new StudentProgressionOperation(
                10L, 1L, 2L, "Batch", "key", hash('a'), hash('b'), 42L, 2001
        ), "Total items must be between 1 and 2000");
    }

    @Test
    void pendingCanMarkRunning() {
        StudentProgressionOperation operation = operation();

        operation.markRunning();

        assertThat(operation.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.RUNNING);
        assertThat(operation.getStartedAt()).isNotNull();
    }

    @Test
    void runningCanMarkSucceeded() {
        StudentProgressionOperation operation = runningOperation();

        operation.markSucceeded("{\"ok\":true}");

        assertThat(operation.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.SUCCEEDED);
        assertThat(operation.getResultJson()).isEqualTo("{\"ok\":true}");
        assertThat(operation.getFailureCode()).isNull();
        assertThat(operation.getFailureMessage()).isNull();
        assertThat(operation.getCompletedAt()).isNotNull();
    }

    @Test
    void runningCanMarkFailedStalePreviewAndConflicted() {
        StudentProgressionOperation failed = runningOperation();
        StudentProgressionOperation stale = runningOperation();
        StudentProgressionOperation conflicted = runningOperation();

        failed.markFailed("FAILED_CODE", "Failed message");
        stale.markStalePreview("STALE_PREVIEW", "Stale preview");
        conflicted.markConflicted("CONFLICTED", "Conflicted");

        assertThat(failed.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.FAILED);
        assertThat(stale.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.STALE_PREVIEW);
        assertThat(conflicted.getStatus())
                .isEqualTo(StudentProgressionOperationStatus.CONFLICTED);
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        StudentProgressionOperation pending = operation();
        StudentProgressionOperation succeeded = runningOperation();
        succeeded.markSucceeded("{\"ok\":true}");

        assertThatThrownBy(() -> pending.markSucceeded("{\"ok\":true}"))
                .isInstanceOf(StudentProgressionConflictException.class)
                .hasMessage("Progression operation must be RUNNING but is PENDING");
        assertThatThrownBy(succeeded::markRunning)
                .isInstanceOf(StudentProgressionConflictException.class)
                .hasMessage("Progression operation must be PENDING but is SUCCEEDED");
        assertThatThrownBy(() -> succeeded.markFailed("CODE", "Message"))
                .isInstanceOf(StudentProgressionConflictException.class)
                .hasMessage("Progression operation must be RUNNING but is SUCCEEDED");
    }

    @Test
    void validatesResultAndFailureMetadata() {
        assertInvalid(() -> runningOperation().markSucceeded(" "),
                "Successful operation result JSON is required");
        assertInvalid(() -> runningOperation().markFailed(" ", "Message"),
                "Failure code is required");
        assertInvalid(() -> runningOperation().markFailed("CODE", " "),
                "Failure message is required");
        assertInvalid(() -> runningOperation().markFailed(
                "A".repeat(81), "Message"
        ), "Failure code cannot exceed 80 characters");
        assertInvalid(() -> runningOperation().markFailed(
                "CODE", "A".repeat(501)
        ), "Failure message cannot exceed 500 characters");
    }

    private static StudentProgressionOperation runningOperation() {
        StudentProgressionOperation operation = operation();
        operation.markRunning();
        return operation;
    }

    private static StudentProgressionOperation operation() {
        return new StudentProgressionOperation(
                10L,
                1L,
                2L,
                "Annual promotion",
                "key-1",
                hash('a'),
                hash('b'),
                42L,
                1
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

    private static String hash(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }

    private interface ThrowingRunnable {
        void run();
    }
}
