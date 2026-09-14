package com.dawnrise.academic.studentattendance.reporting.service;

import com.dawnrise.academic.studentattendance.reporting.dto.*;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public interface StudentAttendanceReportService {

    DailySectionAttendanceReportResponse getDailySectionReport(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate
    );

    StudentAttendanceHistoryReportResponse getStudentHistory(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long studentUserId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );

    MonthlyStudentAttendanceReportResponse getStudentMonthlySummary(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long studentUserId,
            YearMonth month
    );

    SectionAttendanceSummaryResponse getSectionSummary(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate fromDate,
            LocalDate toDate
    );

    GradeAttendanceSummaryResponse getGradeSummary(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            LocalDate fromDate,
            LocalDate toDate
    );

    LowAttendanceReportResponse getLowAttendanceStudents(
            long organizationId,
            long actorUserId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal thresholdPercentage,
            Pageable pageable
    );

    MissingAttendanceReportResponse getMissingAttendance(
            long organizationId,
            long actorUserId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );
}