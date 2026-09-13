package com.dawnrise.academic.studentattendance.offlinesync.entity;

import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncFailureCategory;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncMode;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncStatus;
import com.dawnrise.academic.studentattendance.offlinesync.exception.InvalidStudentAttendanceOfflineSyncException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceOfflineSyncOperationTest {

    @Test
    void successfulOperationStoresImmutableReplayResult() {
        StudentAttendanceOfflineSyncOperation operation = operation();

        operation.succeed(31L, "{\"operationId\":1}");

        assertThat(operation.getStatus()).isEqualTo(StudentAttendanceOfflineSyncStatus.SUCCEEDED);
        assertThat(operation.getAttendanceSessionId()).isEqualTo(31L);
        assertThat(operation.getResultJson()).contains("operationId");
        assertThat(operation.getCompletedAt()).isNotNull();
        assertThatThrownBy(() -> operation.fail(
                StudentAttendanceOfflineSyncFailureCategory.CONFLICT, "late"
        )).isInstanceOf(InvalidStudentAttendanceOfflineSyncException.class);
    }

    @Test
    void failedOperationStoresSafeCategoryAndMessage() {
        StudentAttendanceOfflineSyncOperation operation = operation();

        operation.fail(StudentAttendanceOfflineSyncFailureCategory.CONFLICT, "stale version");

        assertThat(operation.getStatus()).isEqualTo(StudentAttendanceOfflineSyncStatus.FAILED);
        assertThat(operation.getFailureCategory())
                .isEqualTo(StudentAttendanceOfflineSyncFailureCategory.CONFLICT);
        assertThat(operation.getFailureMessage()).isEqualTo("stale version");
    }

    private StudentAttendanceOfflineSyncOperation operation() {
        return new StudentAttendanceOfflineSyncOperation(
                7L, 11L, 3L, 3L, 3L,
                LocalDate.of(2026, 9, 11),
                "device-operation-1",
                "sha256:" + "a".repeat(64),
                StudentAttendanceOfflineSyncMode.PARTIAL
        );
    }
}
