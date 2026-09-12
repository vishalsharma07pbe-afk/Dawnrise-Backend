package com.dawnrise.academic.studentattendance.correction.entity;

import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;
import com.dawnrise.academic.studentattendance.correction.exception.InvalidStudentAttendanceCorrectionException;
import com.dawnrise.academic.studentattendance.correction.exception.StudentAttendanceCorrectionConflictException;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceCorrectionRequestTest {

    @Test
    void createsPendingRequestFromSubmittedSessionContext() {
        StudentAttendanceCorrectionRequest request = request();

        assertThat(request.getStatus()).isEqualTo(StudentAttendanceCorrectionStatus.PENDING);
        assertThat(request.getOrganizationId()).isEqualTo(11L);
        assertThat(request.getRequestedByUserId()).isEqualTo(91L);
    }

    @Test
    void requesterCanCancelPendingRequest() {
        StudentAttendanceCorrectionRequest request = request();
        ReflectionTestUtils.setField(request, "version", 0L);

        request.cancel(91L, 0L, null, OffsetDateTime.parse("2026-09-12T08:00:00Z"));

        assertThat(request.getStatus()).isEqualTo(StudentAttendanceCorrectionStatus.CANCELLED);
        assertThat(request.getReviewedByUserId()).isNull();
        assertThat(request.getReviewComment()).isNull();
        assertThat(request.getReviewedAt()).isNotNull();
    }

    @Test
    void cancelAcceptsOptionalComment() {
        StudentAttendanceCorrectionRequest request = request();
        ReflectionTestUtils.setField(request, "version", 0L);

        request.cancel(91L, 0L, "No longer needed", OffsetDateTime.parse("2026-09-12T08:00:00Z"));

        assertThat(request.getStatus()).isEqualTo(StudentAttendanceCorrectionStatus.CANCELLED);
        assertThat(request.getReviewComment()).isEqualTo("No longer needed");
    }

    @Test
    void approveLeavesReviewCommentEmpty() {
        StudentAttendanceCorrectionRequest request = request();
        ReflectionTestUtils.setField(request, "version", 0L);

        request.approve(92L, 0L, null, OffsetDateTime.parse("2026-09-12T08:00:00Z"));

        assertThat(request.getStatus()).isEqualTo(StudentAttendanceCorrectionStatus.APPROVED);
        assertThat(request.getReviewedByUserId()).isEqualTo(92L);
        assertThat(request.getReviewComment()).isNull();
        assertThat(request.getReviewedAt()).isNotNull();
    }

    @Test
    void approveAcceptsOptionalComment() {
        StudentAttendanceCorrectionRequest request = request();
        ReflectionTestUtils.setField(request, "version", 0L);

        request.approve(92L, 0L, "Looks right", OffsetDateTime.parse("2026-09-12T08:00:00Z"));

        assertThat(request.getStatus()).isEqualTo(StudentAttendanceCorrectionStatus.APPROVED);
        assertThat(request.getReviewComment()).isEqualTo("Looks right");
    }

    @Test
    void rejectRequiresReviewComment() {
        StudentAttendanceCorrectionRequest request = request();
        ReflectionTestUtils.setField(request, "version", 0L);

        assertThatThrownBy(() ->
                request.reject(92L, 0L, null, OffsetDateTime.parse("2026-09-12T08:00:00Z")))
                .isInstanceOf(InvalidStudentAttendanceCorrectionException.class)
                .hasMessage("Review comment is required");
    }

    @Test
    void rejectAcceptsReviewComment() {
        StudentAttendanceCorrectionRequest request = request();
        ReflectionTestUtils.setField(request, "version", 0L);

        request.reject(92L, 0L, "Needs evidence", OffsetDateTime.parse("2026-09-12T08:00:00Z"));

        assertThat(request.getStatus()).isEqualTo(StudentAttendanceCorrectionStatus.REJECTED);
        assertThat(request.getReviewedByUserId()).isEqualTo(92L);
        assertThat(request.getReviewComment()).isEqualTo("Needs evidence");
        assertThat(request.getReviewedAt()).isNotNull();
    }

    @Test
    void requesterSelfReviewIsRejected() {
        StudentAttendanceCorrectionRequest request = request();
        ReflectionTestUtils.setField(request, "version", 0L);

        assertThatThrownBy(() ->
                request.approve(91L, 0L, null, OffsetDateTime.parse("2026-09-12T08:00:00Z")))
                .isInstanceOf(StudentAttendanceCorrectionConflictException.class);
    }

    @Test
    void staleVersionIsRejected() {
        StudentAttendanceCorrectionRequest request = request();
        ReflectionTestUtils.setField(request, "version", 2L);

        assertThatThrownBy(() ->
                request.cancel(91L, 1L, null, OffsetDateTime.parse("2026-09-12T08:00:00Z")))
                .isInstanceOf(StudentAttendanceCorrectionConflictException.class);
    }

    private StudentAttendanceCorrectionRequest request() {
        StudentAttendanceSession session = new StudentAttendanceSession(
                11L,
                21L,
                31L,
                41L,
                51L,
                LocalDate.of(2026, 9, 12),
                91L
        );
        ReflectionTestUtils.setField(session, "id", 61L);
        return new StudentAttendanceCorrectionRequest(
                session,
                "Wrong mark",
                91L,
                OffsetDateTime.parse("2026-09-12T07:00:00Z")
        );
    }
}
