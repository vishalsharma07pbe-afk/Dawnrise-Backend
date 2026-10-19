package com.dawnrise.academic.gradelevel.repository;

import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface GradeLevelRepository
        extends JpaRepository<GradeLevel, Long> {

    Optional<GradeLevel>
    findByIdAndAcademicYearIdAndOrganizationId(
            Long id,
            Long academicYearId,
            Long organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT grade FROM GradeLevel grade
            WHERE grade.id = :id
              AND grade.academicYearId = :academicYearId
              AND grade.organizationId = :organizationId
            """)
    Optional<GradeLevel> findScopeForUpdate(
            @Param("id") Long id,
            @Param("academicYearId") Long academicYearId,
            @Param("organizationId") Long organizationId
    );

    Optional<GradeLevel> findByOrganizationIdAndAcademicYearIdAndCodeIgnoreCase(
            Long organizationId,
            Long academicYearId,
            String code
    );

    List<GradeLevel>
    findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAsc(
            Long organizationId,
            Long academicYearId
    );

    List<GradeLevel>
    findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc(
            Long organizationId,
            Long academicYearId
    );

    List<GradeLevel> findAllByOrganizationIdAndAcademicYearIdAndIdIn(
            Long organizationId,
            Long academicYearId,
            Collection<Long> gradeLevelIds
    );

    boolean existsByAcademicYearIdAndCodeIgnoreCase(
            Long academicYearId,
            String code
    );

    boolean existsByAcademicYearIdAndNameIgnoreCase(
            Long academicYearId,
            String name
    );

    boolean existsByAcademicYearIdAndDisplayOrder(
            Long academicYearId,
            Integer displayOrder
    );

    boolean existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot(
            Long academicYearId,
            String code,
            Long gradeLevelId
    );

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(
            Long academicYearId,
            String name,
            Long gradeLevelId
    );

    boolean existsByAcademicYearIdAndDisplayOrderAndIdNot(
            Long academicYearId,
            Integer displayOrder,
            Long gradeLevelId
    );

    boolean existsByOrganizationIdAndAcademicYearId(
            Long organizationId,
            Long academicYearId
    );
}
