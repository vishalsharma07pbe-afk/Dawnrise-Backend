package com.dawnrise.academic.academicyear.repository;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository
        extends JpaRepository<AcademicYear, Long> {

    Optional<AcademicYear> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    List<AcademicYear> findAllByOrganizationIdOrderByStartDateDesc(
            Long organizationId
    );

    Optional<AcademicYear> findByOrganizationIdAndStatus(
            Long organizationId,
            AcademicYearStatus status
    );

    boolean existsByOrganizationIdAndNameIgnoreCase(
            Long organizationId,
            String name
    );

    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
            Long organizationId,
            String name,
            Long academicYearId
    );

    @Query("""
        SELECT COUNT(academicYear) > 0
        FROM AcademicYear academicYear
        WHERE academicYear.organizationId = :organizationId
          AND academicYear.id <> :academicYearId
          AND academicYear.startDate <= :endDate
          AND academicYear.endDate >= :startDate
        """)
    boolean existsOverlappingPeriodExcludingId(
            @Param("organizationId") long organizationId,
            @Param("academicYearId") long academicYearId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT COUNT(academicYear) > 0
        FROM AcademicYear academicYear
        WHERE academicYear.organizationId = :organizationId
          AND academicYear.startDate <= :endDate
          AND academicYear.endDate >= :startDate
        """)
    boolean existsOverlappingPeriod(
            @Param("organizationId") long organizationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}