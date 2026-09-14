package com.dawnrise.academic.studentattendance.reporting.validation;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentattendance.reporting.config.StudentAttendanceReportingProperties;
import com.dawnrise.academic.studentattendance.reporting.dto.AttendanceReportDateRange;
import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import com.dawnrise.academic.studentattendance.reporting.exception.StudentAttendanceReportNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class StudentAttendanceReportValidatorImpl
        implements StudentAttendanceReportValidator {

    private static final BigDecimal MAX_PERCENTAGE =
            BigDecimal.valueOf(100);

    private final AcademicYearRepository
            academicYearRepository;

    private final GradeLevelRepository
            gradeLevelRepository;

    private final SectionRepository
            sectionRepository;

    private final StudentAttendanceReportingProperties
            properties;

    public StudentAttendanceReportValidatorImpl(
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SectionRepository sectionRepository,
            StudentAttendanceReportingProperties properties
    ) {
        this.academicYearRepository =
                academicYearRepository;
        this.gradeLevelRepository =
                gradeLevelRepository;
        this.sectionRepository =
                sectionRepository;
        this.properties = properties;
    }

    @Override
    public AcademicYear requireAcademicYear(
            long organizationId,
            long academicYearId
    ) {
        requirePositive(
                organizationId,
                "Organization ID must be positive"
        );

        requirePositive(
                academicYearId,
                "Academic year ID must be positive"
        );

        return academicYearRepository
                .findByIdAndOrganizationId(
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new StudentAttendanceReportNotFoundException(
                                "Academic year was not found"
                        )
                );
    }

    @Override
    public void requireGradeLevel(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    ) {
        requirePositive(
                gradeLevelId,
                "Grade level ID must be positive"
        );

        gradeLevelRepository
                .findByIdAndAcademicYearIdAndOrganizationId(
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new StudentAttendanceReportNotFoundException(
                                "Grade level was not found"
                        )
                );
    }

    @Override
    public void requireSection(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    ) {
        requirePositive(
                sectionId,
                "Section ID must be positive"
        );

        sectionRepository
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        sectionId,
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new StudentAttendanceReportNotFoundException(
                                "Section was not found"
                        )
                );
    }

    @Override
    public AttendanceReportDateRange requireDateRange(
            AcademicYear academicYear,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (academicYear == null) {
            throw new InvalidStudentAttendanceReportException(
                    "Academic year is required"
            );
        }

        AttendanceReportDateRange dateRange;

        try {
            dateRange = new AttendanceReportDateRange(
                    fromDate,
                    toDate
            );
        } catch (NullPointerException
                 | IllegalArgumentException exception) {
            throw new InvalidStudentAttendanceReportException(
                    exception.getMessage(),
                    exception
            );
        }

        if (dateRange.fromDate().isBefore(
                academicYear.getStartDate()
        ) || dateRange.toDate().isAfter(
                academicYear.getEndDate()
        )) {
            throw new InvalidStudentAttendanceReportException(
                    "Report date range must be within "
                            + "the academic year"
            );
        }

        if (dateRange.inclusiveDayCount()
                > properties.getMaxDateRangeDays()) {
            throw new InvalidStudentAttendanceReportException(
                    "Report date range cannot exceed "
                            + properties.getMaxDateRangeDays()
                            + " days"
            );
        }

        return dateRange;
    }

    @Override
    public AttendanceReportDateRange requireMonthRange(
            AcademicYear academicYear,
            YearMonth month
    ) {
        if (academicYear == null) {
            throw new InvalidStudentAttendanceReportException(
                    "Academic year is required"
            );
        }

        if (month == null) {
            throw new InvalidStudentAttendanceReportException(
                    "Report month is required"
            );
        }

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        LocalDate effectiveStart =
                monthStart.isBefore(academicYear.getStartDate())
                        ? academicYear.getStartDate()
                        : monthStart;

        LocalDate effectiveEnd =
                monthEnd.isAfter(academicYear.getEndDate())
                        ? academicYear.getEndDate()
                        : monthEnd;

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new InvalidStudentAttendanceReportException(
                    "Report month is outside the academic year"
            );
        }

        return requireDateRange(
                academicYear,
                effectiveStart,
                effectiveEnd
        );
    }

    @Override
    public Pageable sanitizePageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(
                    0,
                    Math.min(
                            20,
                            properties.getMaxPageSize()
                    )
            );
        }

        if (pageable.getPageNumber() < 0) {
            throw new InvalidStudentAttendanceReportException(
                    "Page number cannot be negative"
            );
        }

        if (pageable.getPageSize() <= 0
                || pageable.getPageSize()
                > properties.getMaxPageSize()) {
            throw new InvalidStudentAttendanceReportException(
                    "Page size must be between 1 and "
                            + properties.getMaxPageSize()
            );
        }

        /*
         * Native reporting queries have deterministic SQL ordering.
         * Do not append arbitrary client-provided sort properties.
         */
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize()
        );
    }

    @Override
    public BigDecimal requireThresholdPercentage(
            BigDecimal thresholdPercentage
    ) {
        if (thresholdPercentage == null) {
            throw new InvalidStudentAttendanceReportException(
                    "Attendance threshold is required"
            );
        }

        if (thresholdPercentage.signum() < 0
                || thresholdPercentage.compareTo(
                MAX_PERCENTAGE
        ) > 0) {
            throw new InvalidStudentAttendanceReportException(
                    "Attendance threshold must be "
                            + "between 0 and 100"
            );
        }

        return thresholdPercentage.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    @Override
    public void requireOptionalScope(
            long organizationId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId
    ) {
        if (sectionId != null && gradeLevelId == null) {
            throw new InvalidStudentAttendanceReportException(
                    "Grade level ID is required "
                            + "when section ID is provided"
            );
        }

        if (gradeLevelId == null) {
            return;
        }

        requireGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        if (sectionId != null) {
            requireSection(
                    organizationId,
                    academicYearId,
                    gradeLevelId,
                    sectionId
            );
        }
    }

    private void requirePositive(
            long value,
            String message
    ) {
        if (value <= 0) {
            throw new InvalidStudentAttendanceReportException(
                    message
            );
        }
    }
}