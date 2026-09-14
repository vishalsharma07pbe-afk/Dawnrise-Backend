package com.dawnrise.academic.studentattendance.reporting.validation;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentattendance.reporting.config.StudentAttendanceReportingProperties;
import com.dawnrise.academic.studentattendance.reporting.dto.AttendanceReportDateRange;
import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import com.dawnrise.academic.studentattendance.reporting.exception.StudentAttendanceReportNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudentAttendanceReportValidatorImplTest {

    private AcademicYearRepository academicYearRepository;
    private GradeLevelRepository gradeLevelRepository;
    private SectionRepository sectionRepository;
    private StudentAttendanceReportValidatorImpl validator;
    private AcademicYear academicYear;

    @BeforeEach
    void setUp() {
        academicYearRepository = mock(AcademicYearRepository.class);
        gradeLevelRepository = mock(GradeLevelRepository.class);
        sectionRepository = mock(SectionRepository.class);
        StudentAttendanceReportingProperties properties =
                new StudentAttendanceReportingProperties();
        properties.setMaxDateRangeDays(31);
        properties.setMaxPageSize(50);
        validator = new StudentAttendanceReportValidatorImpl(
                academicYearRepository,
                gradeLevelRepository,
                sectionRepository,
                properties
        );
        academicYear = new AcademicYear(
                7L,
                "2026",
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2027, 3, 20)
        );
    }

    @Test
    void academicYearLookupIsTenantScoped() {
        when(academicYearRepository.findByIdAndOrganizationId(8L, 7L))
                .thenReturn(Optional.of(academicYear));

        assertThat(validator.requireAcademicYear(7L, 8L))
                .isSameAs(academicYear);
        verify(academicYearRepository)
                .findByIdAndOrganizationId(8L, 7L);

        assertThatThrownBy(() -> validator.requireAcademicYear(7L, 99L))
                .isInstanceOf(StudentAttendanceReportNotFoundException.class);
    }

    @Test
    void gradeAndSectionHierarchyValidationIsTenantScoped() {
        when(gradeLevelRepository
                .findByIdAndAcademicYearIdAndOrganizationId(9L, 8L, 7L))
                .thenReturn(Optional.of(mock(GradeLevel.class)));
        when(sectionRepository
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        10L,
                        9L,
                        8L,
                        7L
                )).thenReturn(Optional.of(mock(Section.class)));

        validator.requireGradeLevel(7L, 8L, 9L);
        validator.requireSection(7L, 8L, 9L, 10L);

        verify(gradeLevelRepository)
                .findByIdAndAcademicYearIdAndOrganizationId(9L, 8L, 7L);
        verify(sectionRepository)
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        10L,
                        9L,
                        8L,
                        7L
                );
    }

    @Test
    void dateRangeMustBeInsideAcademicYearAndWithinMaximum() {
        AttendanceReportDateRange range = validator.requireDateRange(
                academicYear,
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 12)
        );

        assertThat(range.inclusiveDayCount()).isEqualTo(3);

        assertThatThrownBy(() -> validator.requireDateRange(
                academicYear,
                LocalDate.of(2026, 4, 9),
                LocalDate.of(2026, 4, 12)
        )).isInstanceOf(InvalidStudentAttendanceReportException.class);
        assertThatThrownBy(() -> validator.requireDateRange(
                academicYear,
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 5, 11)
        )).isInstanceOf(InvalidStudentAttendanceReportException.class)
                .hasMessageContaining("31 days");
    }

    @Test
    void monthRangeHandlesPartialBoundaryMonthsAndRejectsOutsideMonth() {
        AttendanceReportDateRange first = validator.requireMonthRange(
                academicYear,
                YearMonth.of(2026, 4)
        );
        AttendanceReportDateRange last = validator.requireMonthRange(
                academicYear,
                YearMonth.of(2027, 3)
        );

        assertThat(first.fromDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(first.toDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(last.fromDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(last.toDate()).isEqualTo(LocalDate.of(2027, 3, 20));

        assertThatThrownBy(() -> validator.requireMonthRange(
                academicYear,
                YearMonth.of(2026, 3)
        )).isInstanceOf(InvalidStudentAttendanceReportException.class);
    }

    @Test
    void pageableIsBoundedAndClientSortIsRemoved() {
        var pageable = validator.sanitizePageable(
                PageRequest.of(
                        2,
                        25,
                        Sort.by("student_user_id")
                )
        );

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort().isUnsorted()).isTrue();

        assertThatThrownBy(() -> validator.sanitizePageable(
                PageRequest.of(0, 51)
        )).isInstanceOf(InvalidStudentAttendanceReportException.class);
    }

    @Test
    void thresholdIsNormalizedAndConstrained() {
        assertThat(validator.requireThresholdPercentage(
                new BigDecimal("75.555")
        )).isEqualByComparingTo("75.56");

        assertThatThrownBy(() ->
                validator.requireThresholdPercentage(new BigDecimal("-0.01"))
        ).isInstanceOf(InvalidStudentAttendanceReportException.class);
        assertThatThrownBy(() ->
                validator.requireThresholdPercentage(new BigDecimal("100.01"))
        ).isInstanceOf(InvalidStudentAttendanceReportException.class);
    }

    @Test
    void optionalScopeRejectsSectionWithoutGradeAndValidatesHierarchy() {
        assertThatThrownBy(() -> validator.requireOptionalScope(
                7L,
                8L,
                null,
                10L
        )).isInstanceOf(InvalidStudentAttendanceReportException.class);

        when(gradeLevelRepository
                .findByIdAndAcademicYearIdAndOrganizationId(9L, 8L, 7L))
                .thenReturn(Optional.of(mock(GradeLevel.class)));
        when(sectionRepository
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        10L,
                        9L,
                        8L,
                        7L
                )).thenReturn(Optional.of(mock(Section.class)));

        validator.requireOptionalScope(7L, 8L, 9L, 10L);

        verify(gradeLevelRepository)
                .findByIdAndAcademicYearIdAndOrganizationId(9L, 8L, 7L);
        verify(sectionRepository)
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        10L,
                        9L,
                        8L,
                        7L
                );
    }
}
