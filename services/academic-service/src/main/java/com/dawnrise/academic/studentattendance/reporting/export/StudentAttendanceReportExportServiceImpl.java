package com.dawnrise.academic.studentattendance.reporting.export;

import com.dawnrise.academic.studentattendance.reporting.config.StudentAttendanceReportingProperties;
import com.dawnrise.academic.studentattendance.reporting.dto.*;
import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import com.dawnrise.academic.studentattendance.reporting.service.StudentAttendanceReportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class StudentAttendanceReportExportServiceImpl
        implements StudentAttendanceReportExportService {

    private static final List<String> DAILY_HEADERS =
            List.of(
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

    private static final List<String> HISTORY_HEADERS =
            List.of(
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

    private static final List<String>
            SECTION_SUMMARY_HEADERS =
            List.of(
                    "Academic Year ID",
                    "Grade Level ID",
                    "Section ID",
                    "From Date",
                    "To Date",
                    "Submitted Sessions",
                    "Distinct Students",
                    "Present",
                    "Absent",
                    "Late",
                    "Half Day",
                    "Excused",
                    "Total Records",
                    "Earned Credit",
                    "Possible Credit",
                    "Attendance Percentage"
            );

    private static final List<String>
            LOW_ATTENDANCE_HEADERS =
            List.of(
                    "Student Enrollment ID",
                    "Student User ID",
                    "Grade Level ID",
                    "Section ID",
                    "Roll Number",
                    "Present",
                    "Absent",
                    "Late",
                    "Half Day",
                    "Excused",
                    "Total Records",
                    "Earned Credit",
                    "Possible Credit",
                    "Attendance Percentage"
            );

    private final StudentAttendanceReportService
            reportService;

    private final StudentAttendanceReportFileWriter
            fileWriter;

    private final StudentAttendanceReportingProperties
            properties;

    public StudentAttendanceReportExportServiceImpl(
            StudentAttendanceReportService reportService,
            StudentAttendanceReportFileWriter fileWriter,
            StudentAttendanceReportingProperties properties
    ) {
        this.reportService = reportService;
        this.fileWriter = fileWriter;
        this.properties = properties;
    }

    @Override
    public StudentAttendanceReportExport exportDailySection(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate,
            StudentAttendanceReportExportFormat format
    ) {
        requireFormat(format);

        DailySectionAttendanceReportResponse report =
                reportService.getDailySectionReport(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        attendanceDate
                );

        requireWithinExportLimit(
                report.students().size()
        );

        List<List<Object>> rows =
                report.students()
                        .stream()
                        .map(student ->
                                dailyRow(
                                        report.attendanceDate(),
                                        student
                                )
                        )
                        .toList();

        return buildExport(
                "student-attendance-daily-section-"
                        + sectionId
                        + "-"
                        + attendanceDate,
                format,
                DAILY_HEADERS,
                rows
        );
    }

    @Override
    public StudentAttendanceReportExport
    exportStudentHistory(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long studentUserId,
            LocalDate fromDate,
            LocalDate toDate,
            StudentAttendanceReportExportFormat format
    ) {
        requireFormat(format);

        int batchSize = exportBatchSize();

        StudentAttendanceHistoryReportResponse firstPage =
                reportService.getStudentHistory(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        studentUserId,
                        fromDate,
                        toDate,
                        PageRequest.of(0, batchSize)
                );

        requireWithinExportLimit(
                firstPage.records().totalElements()
        );

        List<StudentAttendanceHistoryRowResponse>
                records =
                new ArrayList<>(
                        firstPage.records().content()
                );

        for (int pageNumber = 1;
             pageNumber
                     < firstPage.records().totalPages();
             pageNumber++) {

            StudentAttendanceHistoryReportResponse page =
                    reportService.getStudentHistory(
                            organizationId,
                            actorUserId,
                            academicYearId,
                            gradeLevelId,
                            sectionId,
                            studentUserId,
                            fromDate,
                            toDate,
                            PageRequest.of(
                                    pageNumber,
                                    batchSize
                            )
                    );

            requireConsistentPage(
                    firstPage.records(),
                    page.records(),
                    pageNumber
            );

            records.addAll(
                    page.records().content()
            );
        }

        requireExportedRowCountMatchesTotal(
                records.size(),
                firstPage.records().totalElements()
        );
        requireWithinExportLimit(records.size());

        List<List<Object>> rows =
                records.stream()
                        .map(this::historyRow)
                        .toList();

        return buildExport(
                "student-attendance-history-"
                        + studentUserId
                        + "-"
                        + fromDate
                        + "-"
                        + toDate,
                format,
                HISTORY_HEADERS,
                rows
        );
    }

    @Override
    public StudentAttendanceReportExport
    exportSectionSummary(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            StudentAttendanceReportExportFormat format
    ) {
        requireFormat(format);

        SectionAttendanceSummaryResponse report =
                reportService.getSectionSummary(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        fromDate,
                        toDate
                );

        List<List<Object>> rows =
                List.of(sectionSummaryRow(report));

        return buildExport(
                "student-attendance-section-summary-"
                        + sectionId
                        + "-"
                        + fromDate
                        + "-"
                        + toDate,
                format,
                SECTION_SUMMARY_HEADERS,
                rows
        );
    }

    @Override
    public StudentAttendanceReportExport
    exportLowAttendance(
            long organizationId,
            long actorUserId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal thresholdPercentage,
            StudentAttendanceReportExportFormat format
    ) {
        requireFormat(format);

        int batchSize = exportBatchSize();

        LowAttendanceReportResponse firstPage =
                reportService.getLowAttendanceStudents(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        fromDate,
                        toDate,
                        thresholdPercentage,
                        PageRequest.of(0, batchSize)
                );

        requireWithinExportLimit(
                firstPage.students().totalElements()
        );

        List<LowAttendanceStudentResponse> students =
                new ArrayList<>(
                        firstPage.students().content()
                );

        for (int pageNumber = 1;
             pageNumber
                     < firstPage.students().totalPages();
             pageNumber++) {

            LowAttendanceReportResponse page =
                    reportService.getLowAttendanceStudents(
                            organizationId,
                            actorUserId,
                            academicYearId,
                            gradeLevelId,
                            sectionId,
                            fromDate,
                            toDate,
                            thresholdPercentage,
                            PageRequest.of(
                                    pageNumber,
                                    batchSize
                            )
                    );

            requireConsistentPage(
                    firstPage.students(),
                    page.students(),
                    pageNumber
            );

            students.addAll(
                    page.students().content()
            );
        }

        requireExportedRowCountMatchesTotal(
                students.size(),
                firstPage.students().totalElements()
        );
        requireWithinExportLimit(students.size());

        List<List<Object>> rows =
                students.stream()
                        .map(this::lowAttendanceRow)
                        .toList();

        String scope;

        if (sectionId != null) {
            scope = "section-" + sectionId;
        } else if (gradeLevelId != null) {
            scope = "grade-" + gradeLevelId;
        } else {
            scope = "academic-year-"
                    + academicYearId;
        }

        return buildExport(
                "student-attendance-low-"
                        + scope
                        + "-"
                        + fromDate
                        + "-"
                        + toDate,
                format,
                LOW_ATTENDANCE_HEADERS,
                rows
        );
    }

    private List<Object> dailyRow(
            LocalDate attendanceDate,
            DailyStudentAttendanceRowResponse student
    ) {
        AttendanceCreditSummary credits =
                AttendanceCreditSummary.from(
                        student.earnedCredit(),
                        student.possibleCredit()
                );

        return row(
                attendanceDate,
                student.rollNumber(),
                student.studentUserId(),
                student.recordedStatus(),
                student.effectiveStatus(),
                student.earnedCredit(),
                student.possibleCredit(),
                credits.attendancePercentage(),
                student.latePenaltyApplied(),
                student.remarks()
        );
    }

    private List<Object> historyRow(
            StudentAttendanceHistoryRowResponse record
    ) {
        AttendanceCreditSummary credits =
                AttendanceCreditSummary.from(
                        record.earnedCredit(),
                        record.possibleCredit()
                );

        return row(
                record.attendanceDate(),
                record.academicYearId(),
                record.gradeLevelId(),
                record.sectionId(),
                record.studentEnrollmentId(),
                record.recordedStatus(),
                record.effectiveStatus(),
                record.earnedCredit(),
                record.possibleCredit(),
                credits.attendancePercentage(),
                record.latePenaltyApplied(),
                record.submissionType(),
                record.submittedAt(),
                record.lastUpdatedAt(),
                record.remarks()
        );
    }

    private List<Object> sectionSummaryRow(
            SectionAttendanceSummaryResponse report
    ) {
        AttendanceStatusCountSummary counts =
                report.summary().statusCounts();

        AttendanceCreditSummary credits =
                report.summary().credits();

        return row(
                report.academicYearId(),
                report.gradeLevelId(),
                report.sectionId(),
                report.dateRange().fromDate(),
                report.dateRange().toDate(),
                report.submittedSessionCount(),
                report.distinctStudentCount(),
                counts.presentCount(),
                counts.absentCount(),
                counts.lateCount(),
                counts.halfDayCount(),
                counts.excusedCount(),
                counts.totalCount(),
                credits.earnedCredit(),
                credits.possibleCredit(),
                credits.attendancePercentage()
        );
    }

    private List<Object> lowAttendanceRow(
            LowAttendanceStudentResponse student
    ) {
        AttendanceStatusCountSummary counts =
                student.summary().statusCounts();

        AttendanceCreditSummary credits =
                student.summary().credits();

        return row(
                student.studentEnrollmentId(),
                student.studentUserId(),
                student.gradeLevelId(),
                student.sectionId(),
                student.rollNumber(),
                counts.presentCount(),
                counts.absentCount(),
                counts.lateCount(),
                counts.halfDayCount(),
                counts.excusedCount(),
                counts.totalCount(),
                credits.earnedCredit(),
                credits.possibleCredit(),
                credits.attendancePercentage()
        );
    }

    private StudentAttendanceReportExport buildExport(
            String baseFilename,
            StudentAttendanceReportExportFormat format,
            List<String> headers,
            List<? extends List<?>> rows
    ) {
        requireWithinExportLimit(rows.size());

        byte[] content = fileWriter.write(
                format,
                headers,
                rows
        );

        return new StudentAttendanceReportExport(
                baseFilename
                        + "."
                        + format.fileExtension(),
                format.contentType(),
                content
        );
    }

    private void requireFormat(
            StudentAttendanceReportExportFormat format
    ) {
        if (format == null) {
            throw new InvalidStudentAttendanceReportException(
                    "Export format is required"
            );
        }
    }

    private void requireWithinExportLimit(
            long rowCount
    ) {
        if (rowCount
                > properties.getMaxExportRows()) {
            throw new InvalidStudentAttendanceReportException(
                    "Attendance report exceeds the maximum "
                            + "export row limit of "
                            + properties.getMaxExportRows()
            );
        }
    }

    private int exportBatchSize() {
        return Math.min(
                properties.getMaxPageSize(),
                properties.getMaxExportRows()
        );
    }

    private void requireConsistentPage(
            AttendancePageResponse<?> firstPage,
            AttendancePageResponse<?> laterPage,
            int expectedPageNumber
    ) {
        if (laterPage.pageNumber() != expectedPageNumber
                || laterPage.pageSize() != firstPage.pageSize()
                || laterPage.totalElements()
                != firstPage.totalElements()
                || laterPage.totalPages()
                != firstPage.totalPages()) {
            throw new InvalidStudentAttendanceReportException(
                    "Attendance report pagination changed during export"
            );
        }
    }

    private void requireExportedRowCountMatchesTotal(
            int exportedRows,
            long expectedRows
    ) {
        if (exportedRows != expectedRows) {
            throw new InvalidStudentAttendanceReportException(
                    "Attendance report pagination changed during export"
            );
        }
    }

    private List<Object> row(Object... values) {
        /*
         * List.of cannot be used because optional remarks
         * and timestamps may be null.
         */
        return new ArrayList<>(
                Arrays.asList(values)
        );
    }
}
