package com.dawnrise.academic.studentattendance.reporting.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceReportExportTest {

    @Test
    void defensivelyCopiesContentOnConstructionAndAccess() {
        byte[] content = new byte[]{1, 2, 3};

        StudentAttendanceReportExport export =
                new StudentAttendanceReportExport(
                        "student-attendance.csv",
                        StudentAttendanceReportExportFormat.CSV
                                .contentType(),
                        content
                );
        content[0] = 9;
        byte[] returned = export.content();
        returned[1] = 8;

        assertThat(export.content()).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsUnsafeFilenamesAndMissingContent() {
        assertThatThrownBy(() -> new StudentAttendanceReportExport(
                "student/attendance.csv",
                StudentAttendanceReportExportFormat.CSV.contentType(),
                new byte[]{1}
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentAttendanceReportExport(
                "student-attendance.csv\r\nx:y",
                StudentAttendanceReportExportFormat.CSV.contentType(),
                new byte[]{1}
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentAttendanceReportExport(
                "student attendance.csv",
                StudentAttendanceReportExportFormat.CSV.contentType(),
                new byte[]{1}
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentAttendanceReportExport(
                "student-attendance.csv",
                "",
                new byte[]{1}
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentAttendanceReportExport(
                "student-attendance.csv",
                StudentAttendanceReportExportFormat.CSV.contentType(),
                new byte[0]
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsExposeExpectedExtensionsAndMimeTypes() {
        assertThat(StudentAttendanceReportExportFormat.CSV.fileExtension())
                .isEqualTo("csv");
        assertThat(StudentAttendanceReportExportFormat.CSV.contentType())
                .isEqualTo("text/csv;charset=UTF-8");
        assertThat(StudentAttendanceReportExportFormat.XLSX.fileExtension())
                .isEqualTo("xlsx");
        assertThat(StudentAttendanceReportExportFormat.XLSX.contentType())
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet"
                );
    }
}
