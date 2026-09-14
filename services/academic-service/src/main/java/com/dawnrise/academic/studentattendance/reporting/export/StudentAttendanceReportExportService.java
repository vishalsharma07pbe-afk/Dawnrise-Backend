package com.dawnrise.academic.studentattendance.reporting.export;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface StudentAttendanceReportExportService {

    StudentAttendanceReportExport exportDailySection(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate,
            StudentAttendanceReportExportFormat format
    );

    StudentAttendanceReportExport exportStudentHistory(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long studentUserId,
            LocalDate fromDate,
            LocalDate toDate,
            StudentAttendanceReportExportFormat format
    );

    StudentAttendanceReportExport exportSectionSummary(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            StudentAttendanceReportExportFormat format
    );

    StudentAttendanceReportExport exportLowAttendance(
            long organizationId,
            long actorUserId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal thresholdPercentage,
            StudentAttendanceReportExportFormat format
    );
}