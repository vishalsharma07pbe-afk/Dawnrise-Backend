package com.dawnrise.academic.studentattendance.recording.entity;

import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceSessionTest {

    @Test
    void startsAsDraftWithoutSubmissionMetadata() {
        StudentAttendanceSession session = session();

        assertThat(session.getLifecycleStatus()).isEqualTo(StudentAttendanceSessionStatus.DRAFT);
        assertThat(session.getSubmissionType()).isNull();
        assertThat(session.getSubmittedByUserId()).isNull();
        assertThat(session.getSubmittedAt()).isNull();
    }

    @Test
    void manualSubmissionStoresHumanSubmitter() {
        StudentAttendanceSession session = session();

        session.submitManually(61L);

        assertThat(session.getLifecycleStatus()).isEqualTo(StudentAttendanceSessionStatus.SUBMITTED);
        assertThat(session.getSubmissionType()).isEqualTo(StudentAttendanceSubmissionType.MANUAL);
        assertThat(session.getSubmittedByUserId()).isEqualTo(61L);
        assertThat(session.getSubmittedAt()).isNotNull();
    }

    @Test
    void automaticSubmissionDoesNotStoreHumanSubmitter() {
        StudentAttendanceSession session = session();

        session.submitAutomatically();

        assertThat(session.getLifecycleStatus()).isEqualTo(StudentAttendanceSessionStatus.SUBMITTED);
        assertThat(session.getSubmissionType()).isEqualTo(StudentAttendanceSubmissionType.AUTOMATIC);
        assertThat(session.getSubmittedByUserId()).isNull();
        assertThat(session.getSubmittedAt()).isNotNull();
    }

    @Test
    void submittedSessionCannotBeChangedAsDraft() {
        StudentAttendanceSession session = session();
        session.submitManually(61L);

        assertThatThrownBy(() -> session.touch(61L))
                .isInstanceOf(StudentAttendanceRecordingConflictException.class);
    }

    private static StudentAttendanceSession session() {
        return new StudentAttendanceSession(
                11L,
                21L,
                22L,
                23L,
                24L,
                LocalDate.of(2026, 4, 10),
                51L
        );
    }
}
