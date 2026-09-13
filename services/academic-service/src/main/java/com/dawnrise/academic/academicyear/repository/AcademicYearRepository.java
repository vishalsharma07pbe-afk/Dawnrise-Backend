package com.dawnrise.academic.academicyear.repository;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository
        extends JpaRepository<AcademicYear, Long> {

    Optional<AcademicYear> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT academicYear
    FROM AcademicYear academicYear
    WHERE academicYear.id = :id
      AND academicYear.organizationId = :organizationId
    """)
    Optional<AcademicYear> findByIdAndOrganizationIdForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId
    );

    List<AcademicYear> findAllByOrganizationIdOrderByStartDateDesc(
            Long organizationId
    );

    Optional<AcademicYear> findByOrganizationIdAndStatus(
            Long organizationId,
            AcademicYearStatus status
    );

    Optional<AcademicYear> findByOrganizationIdAndNameIgnoreCaseAndStatus(
            Long organizationId,
            String name,
            AcademicYearStatus status
    );

    boolean existsByOrganizationIdAndNameIgnoreCaseAndStatusNot(
            Long organizationId,
            String name,
            AcademicYearStatus excludedStatus
    );

    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNotAndStatusNot(
            Long organizationId,
            String name,
            Long academicYearId,
            AcademicYearStatus excludedStatus
    );

    @Query("""
    SELECT COUNT(academicYear) > 0
    FROM AcademicYear academicYear
    WHERE academicYear.organizationId = :organizationId
      AND academicYear.id <> :academicYearId
      AND academicYear.status <> :excludedStatus
      AND academicYear.startDate <= :endDate
      AND academicYear.endDate >= :startDate
    """)
    boolean existsOverlappingPeriodExcludingId(
            @Param("organizationId") long organizationId,
            @Param("academicYearId") long academicYearId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludedStatus") AcademicYearStatus excludedStatus
    );

    @Query("""
    SELECT COUNT(academicYear) > 0
    FROM AcademicYear academicYear
    WHERE academicYear.organizationId = :organizationId
      AND academicYear.status <> :excludedStatus
      AND academicYear.startDate <= :endDate
      AND academicYear.endDate >= :startDate
    """)
    boolean existsOverlappingPeriod(
            @Param("organizationId") long organizationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludedStatus") AcademicYearStatus excludedStatus
    );
}
