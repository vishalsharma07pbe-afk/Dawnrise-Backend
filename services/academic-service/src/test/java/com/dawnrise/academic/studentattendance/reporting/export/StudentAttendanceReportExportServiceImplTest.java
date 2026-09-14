package com.dawnrise.academic.studentattendance.reporting.export;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;
import com.dawnrise.academic.studentattendance.reporting.config.StudentAttendanceReportingProperties;
import com.dawnrise.academic.studentattendance.reporting.dto.*;
import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import com.dawnrise.academic.studentattendance.reporting.service.StudentAttendanceReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StudentAttendanceReportExportServiceImplTest {

    private StudentAttendanceReportService reportService;
    private StudentAttendanceReportFileWriter fileWriter;
    private StudentAttendanceReportExportServiceImpl service;

    @BeforeEach
    void setUp() {
        reportService = mock(StudentAttendanceReportService.class);
        fileWriter = mock(StudentAttendanceReportFileWriter.class);
        StudentAttendanceReportingProperties properties =
                new StudentAttendanceReportingProperties();
        properties.setMaxPageSize(2);
        properties.setMaxExportRows(3);
        service = new StudentAttendanceReportExportServiceImpl(
                reportService,
                fileWriter,
                properties
        );
        when(fileWriter.write(any(), anyList(), anyList()))
                .thenReturn("export".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNullFormatSafely() {
        assertThatThrownBy(() -> service.exportDailySection(
                7L,
                11L,
                8L,
                9L,
                10L,
                LocalDate.of(2026, 9, 11),
                null
        )).isInstanceOf(InvalidStudentAttendanceReportException.class)
                .hasMessage("Export format is required");

        verifyNoInteractions(reportService);
    }

    @Test
    void serviceUsesRepeatableReadReadOnlyTransaction() {
        Transactional transactional =
                StudentAttendanceReportExportServiceImpl.class
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation())
                .isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void dailyExportUsesReportServiceAndWritesCsvWithMatchingHeadersAndRows() {
        LocalDate date = LocalDate.of(2026, 9, 11);
        when(reportService.getDailySectionReport(
                7L,
                11L,
                8L,
                9L,
                10L,
                date
        )).thenReturn(new DailySectionAttendanceReportResponse(
                20L,
                7L,
                8L,
                9L,
                10L,
                30L,
                date,
                StudentAttendanceSessionStatus.SUBMITTED,
                StudentAttendanceSubmissionType.MANUAL,
                OffsetDateTime.parse("2026-09-11T04:30:00Z"),
                AttendanceSummary.empty(),
                List.of(new DailyStudentAttendanceRowResponse(
                        1L,
                        2L,
                        9007199254740993L,
                        "A-1",
                        AttendanceStatus.PRESENT,
                        AttendanceStatus.PRESENT,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        false,
                        null
                ))
        ));

        StudentAttendanceReportExport export =
                service.exportDailySection(
                        7L,
                        11L,
                        8L,
                        9L,
                        10L,
                        date,
                        StudentAttendanceReportExportFormat.CSV
                );

        CapturedTable table = capturedTable();
        assertThat(table.headers()).containsExactly(
                "Attendance Date",
                "Roll Number",
                "Student User ID",
                "Recorded Status",
                "Effective Status",
                "Earned Credit",
                "Possible Credit",
                "Attendance Percentage",
                "Late Penalty Applied",
                "Remarks"
        );
        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().get(0)).containsExactly(
                date,
                "A-1",
                9007199254740993L,
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("100.00"),
                false,
                null
        );
        assertThat(export.filename())
                .isEqualTo("student-attendance-daily-section-10-2026-09-11.csv");
        assertThat(export.contentType())
                .isEqualTo(StudentAttendanceReportExportFormat.CSV
                        .contentType());
    }

    @Test
    void historyExportUsesFirstPageMetadataForLimitBeforeFetchingMorePages() {
        when(reportService.getStudentHistory(
                eq(7L),
                eq(11L),
                eq(8L),
                eq(9L),
                eq(10L),
                eq(99L),
                eq(LocalDate.of(2026, 9, 1)),
                eq(LocalDate.of(2026, 9, 30)),
                eq(PageRequest.of(0, 2))
        )).thenReturn(historyReport(historyPage(
                List.of(historyRow(1L)),
                0,
                2,
                4,
                2
        )));

        assertThatThrownBy(() -> service.exportStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                StudentAttendanceReportExportFormat.CSV
        )).isInstanceOf(InvalidStudentAttendanceReportException.class)
                .hasMessageContaining("maximum export row limit");

        verify(reportService, times(1)).getStudentHistory(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                any(),
                any(),
                any()
        );
        verifyNoInteractions(fileWriter);
    }

    @Test
    void historyExportFetchesMultiplePagesWithoutGapsOrDuplicates() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(reportService.getStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                PageRequest.of(0, 2)
        )).thenReturn(historyReport(historyPage(
                List.of(historyRow(1L), historyRow(2L)),
                0,
                2,
                3,
                2
        )));
        when(reportService.getStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                PageRequest.of(1, 2)
        )).thenReturn(historyReport(historyPage(
                List.of(historyRow(3L)),
                1,
                2,
                3,
                2
        )));

        StudentAttendanceReportExport export =
                service.exportStudentHistory(
                        7L,
                        11L,
                        8L,
                        9L,
                        10L,
                        99L,
                        from,
                        to,
                        StudentAttendanceReportExportFormat.XLSX
                );

        CapturedTable table = capturedTable();
        assertThat(table.rows())
                .extracting(row -> row.get(4))
                .containsExactly(1L, 2L, 3L);
        assertThat(table.headers()).containsExactly(
                "Attendance Date",
                "Academic Year ID",
                "Grade Level ID",
                "Section ID",
                "Student Enrollment ID",
                "Recorded Status",
                "Effective Status",
                "Earned Credit",
                "Possible Credit",
                "Attendance Percentage",
                "Late Penalty Applied",
                "Submission Type",
                "Submitted At",
                "Last Updated At",
                "Remarks"
        );
        assertThat(export.filename())
                .isEqualTo(
                        "student-attendance-history-99-2026-09-01-2026-09-30.xlsx"
                );
        verify(fileWriter).write(
                eq(StudentAttendanceReportExportFormat.XLSX),
                anyList(),
                anyList()
        );
    }

    @Test
    void historyExportRejectsChangedLaterPageMetadata() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(reportService.getStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                PageRequest.of(0, 2)
        )).thenReturn(historyReport(historyPage(
                List.of(historyRow(1L)),
                0,
                2,
                2,
                2
        )));
        when(reportService.getStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                PageRequest.of(1, 2)
        )).thenReturn(historyReport(historyPage(
                List.of(historyRow(2L)),
                1,
                2,
                3,
                2
        )));

        assertThatThrownBy(() -> service.exportStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                StudentAttendanceReportExportFormat.CSV
        )).isInstanceOf(InvalidStudentAttendanceReportException.class)
                .hasMessage(
                        "Attendance report pagination changed during export"
                );
    }

    @Test
    void historyExportRejectsMissingLaterPageContent() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(reportService.getStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                PageRequest.of(0, 2)
        )).thenReturn(historyReport(historyPage(
                List.of(historyRow(1L)),
                0,
                2,
                2,
                2
        )));
        when(reportService.getStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                PageRequest.of(1, 2)
        )).thenReturn(historyReport(historyPage(
                List.of(),
                1,
                2,
                2,
                2
        )));

        assertThatThrownBy(() -> service.exportStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                StudentAttendanceReportExportFormat.CSV
        )).isInstanceOf(InvalidStudentAttendanceReportException.class)
                .hasMessage(
                        "Attendance report pagination changed during export"
                );
    }

    @Test
    void lowAttendanceExportPreservesParametersAcrossPages() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        BigDecimal threshold = new BigDecimal("75.00");
        when(reportService.getLowAttendanceStudents(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to,
                threshold,
                PageRequest.of(0, 2)
        )).thenReturn(lowReport(lowPage(
                List.of(lowRow(1L), lowRow(2L)),
                0,
                2,
                3,
                2
        )));
        when(reportService.getLowAttendanceStudents(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to,
                threshold,
                PageRequest.of(1, 2)
        )).thenReturn(lowReport(lowPage(
                List.of(lowRow(3L)),
                1,
                2,
                3,
                2
        )));

        StudentAttendanceReportExport export =
                service.exportLowAttendance(
                        7L,
                        11L,
                        8L,
                        9L,
                        10L,
                        from,
                        to,
                        threshold,
                        StudentAttendanceReportExportFormat.CSV
                );

        CapturedTable table = capturedTable();
        assertThat(table.rows())
                .extracting(row -> row.get(0))
                .containsExactly(1L, 2L, 3L);
        assertThat(table.rows().get(0)).containsSubsequence(
                1L,
                101L,
                9L,
                10L,
                "A-1",
                1L,
                2L,
                0L,
                0L,
                0L,
                3L,
                new BigDecimal("1.00"),
                new BigDecimal("3.00"),
                new BigDecimal("33.33")
        );
        assertThat(export.filename())
                .isEqualTo(
                        "student-attendance-low-section-10-2026-09-01-2026-09-30.csv"
                );
        verify(reportService).getLowAttendanceStudents(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to,
                threshold,
                PageRequest.of(0, 2)
        );
        verify(reportService).getLowAttendanceStudents(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to,
                threshold,
                PageRequest.of(1, 2)
        );
    }

    @Test
    void emptyHistoryReportGeneratesHeaderOnlyFile() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(reportService.getStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                PageRequest.of(0, 2)
        )).thenReturn(historyReport(historyPage(
                List.of(),
                0,
                2,
                0,
                0
        )));

        service.exportStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                StudentAttendanceReportExportFormat.CSV
        );

        assertThat(capturedTable().rows()).isEmpty();
    }

    @Test
    void sectionSummaryProducesExactlyOneDataRow() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(reportService.getSectionSummary(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to
        )).thenReturn(new SectionAttendanceSummaryResponse(
                7L,
                8L,
                9L,
                10L,
                new AttendanceReportDateRange(from, to),
                2L,
                20L,
                AttendanceSummary.of(
                        10,
                        2,
                        1,
                        0,
                        1,
                        new BigDecimal("11.00"),
                        new BigDecimal("14.00")
                )
        ));

        service.exportSectionSummary(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to,
                StudentAttendanceReportExportFormat.CSV
        );

        CapturedTable table = capturedTable();
        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().get(0)).containsExactly(
                8L,
                9L,
                10L,
                from,
                to,
                2L,
                20L,
                10L,
                2L,
                1L,
                0L,
                1L,
                14L,
                new BigDecimal("11.00"),
                new BigDecimal("14.00"),
                new BigDecimal("78.57")
        );
    }

    @SuppressWarnings("unchecked")
    private CapturedTable capturedTable() {
        ArgumentCaptor<List<String>> headers =
                ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<List<Object>>> rows =
                ArgumentCaptor.forClass(List.class);
        verify(fileWriter, atLeastOnce()).write(
                any(),
                headers.capture(),
                rows.capture()
        );
        return new CapturedTable(
                headers.getValue(),
                rows.getValue()
        );
    }

    private static AttendancePageResponse<StudentAttendanceHistoryRowResponse>
    historyPage(
            List<StudentAttendanceHistoryRowResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages
    ) {
        return new AttendancePageResponse<>(
                content,
                pageNumber,
                pageSize,
                content.size(),
                totalElements,
                totalPages,
                pageNumber == 0,
                pageNumber + 1 >= totalPages,
                content.isEmpty()
        );
    }

    private static AttendancePageResponse<LowAttendanceStudentResponse>
    lowPage(
            List<LowAttendanceStudentResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages
    ) {
        return new AttendancePageResponse<>(
                content,
                pageNumber,
                pageSize,
                content.size(),
                totalElements,
                totalPages,
                pageNumber == 0,
                pageNumber + 1 >= totalPages,
                content.isEmpty()
        );
    }

    private static StudentAttendanceHistoryReportResponse historyReport(
            AttendancePageResponse<StudentAttendanceHistoryRowResponse> page
    ) {
        return new StudentAttendanceHistoryReportResponse(
                7L,
                8L,
                9L,
                10L,
                99L,
                new AttendanceReportDateRange(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30)
                ),
                AttendanceSummary.empty(),
                page
        );
    }

    private static LowAttendanceReportResponse lowReport(
            AttendancePageResponse<LowAttendanceStudentResponse> page
    ) {
        return new LowAttendanceReportResponse(
                7L,
                8L,
                9L,
                10L,
                new AttendanceReportDateRange(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30)
                ),
                new BigDecimal("75.00"),
                page
        );
    }

    private static StudentAttendanceHistoryRowResponse historyRow(
            long enrollmentId
    ) {
        return new StudentAttendanceHistoryRowResponse(
                10L,
                enrollmentId + 100L,
                enrollmentId,
                8L,
                9L,
                10L,
                LocalDate.of(2026, 9, (int) enrollmentId),
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                BigDecimal.ONE,
                BigDecimal.ONE,
                false,
                null,
                StudentAttendanceSubmissionType.MANUAL,
                OffsetDateTime.parse("2026-09-01T04:30:00Z"),
                null
        );
    }

    private static LowAttendanceStudentResponse lowRow(
            long enrollmentId
    ) {
        return new LowAttendanceStudentResponse(
                enrollmentId,
                enrollmentId + 100L,
                9L,
                10L,
                "A-" + enrollmentId,
                AttendanceSummary.of(
                        1,
                        2,
                        0,
                        0,
                        0,
                        new BigDecimal("1.00"),
                        new BigDecimal("3.00")
                )
        );
    }

    private record CapturedTable(
            List<String> headers,
            List<List<Object>> rows
    ) {
    }
}
