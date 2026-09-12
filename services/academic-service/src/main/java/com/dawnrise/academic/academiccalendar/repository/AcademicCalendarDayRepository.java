package com.dawnrise.academic.academiccalendar.repository;

import com.dawnrise.academic.academiccalendar.entity.AcademicCalendarDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AcademicCalendarDayRepository
        extends JpaRepository<AcademicCalendarDay, Long> {

    boolean existsByOrganizationIdAndAcademicYearId(
            Long organizationId,
            Long academicYearId
    );

    Optional<AcademicCalendarDay> findByIdAndOrganizationIdAndAcademicYearId(
            Long id,
            Long organizationId,
            Long academicYearId
    );

    Optional<AcademicCalendarDay> findByOrganizationIdAndAcademicYearIdAndCalendarDate(
            Long organizationId,
            Long academicYearId,
            LocalDate calendarDate
    );

    List<AcademicCalendarDay> findAllByOrganizationIdAndAcademicYearIdAndCalendarDateBetweenOrderByCalendarDateAscIdAsc(
            Long organizationId,
            Long academicYearId,
            LocalDate startDate,
            LocalDate endDate
    );
}
