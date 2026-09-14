package com.dawnrise.academic.studentattendance.reporting.validation;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.studentattendance.reporting.dto.AttendanceReportDateRange;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public interface StudentAttendanceReportValidator {

    AcademicYear requireAcademicYear(
            long organizationId,
            long academicYearId
    );

    void requireGradeLevel(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    );

    void requireSection(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    );

    AttendanceReportDateRange requireDateRange(
            AcademicYear academicYear,
            LocalDate fromDate,
            LocalDate toDate
    );

    AttendanceReportDateRange requireMonthRange(
            AcademicYear academicYear,
            YearMonth month
    );

    Pageable sanitizePageable(Pageable pageable);

    BigDecimal requireThresholdPercentage(
            BigDecimal thresholdPercentage
    );

    void requireOptionalScope(
            long organizationId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId
    );
}