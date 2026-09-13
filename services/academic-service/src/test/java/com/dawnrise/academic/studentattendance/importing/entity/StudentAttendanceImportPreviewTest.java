package com.dawnrise.academic.studentattendance.importing.entity;

import com.dawnrise.academic.studentattendance.importing.enums.StudentAttendanceImportFormat;
import com.dawnrise.academic.studentattendance.importing.exception.InvalidStudentAttendanceImportException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceImportPreviewTest {

    @Test
    void confirmStoresOperationAndIdempotencyKey() {
        StudentAttendanceImportPreview preview = preview(true);

        preview.markConfirmed(19L, " import-1 ");

        assertThat(preview.getConfirmedSyncOperationId()).isEqualTo(19L);
        assertThat(preview.getConfirmedIdempotencyKey()).isEqualTo("import-1");
        assertThatThrownBy(() -> preview.markConfirmed(20L, "import-2"))
                .isInstanceOf(InvalidStudentAttendanceImportException.class);
    }

    @Test
    void invalidPreviewCannotBeConfirmed() {
        assertThatThrownBy(() -> preview(false).markConfirmed(19L, "import-1"))
                .isInstanceOf(InvalidStudentAttendanceImportException.class);
    }

    private StudentAttendanceImportPreview preview(boolean canConfirm) {
        return new StudentAttendanceImportPreview(
                7L, 11L,
                canConfirm ? 3L : null,
                canConfirm ? 3L : null,
                canConfirm ? 3L : null,
                canConfirm ? LocalDate.of(2026, 9, 11) : null,
                "sha256:" + "a".repeat(64),
                "attendance.csv",
                StudentAttendanceImportFormat.CSV,
                1,
                canConfirm,
                "{}",
                OffsetDateTime.now().plusMinutes(30)
        );
    }
}
