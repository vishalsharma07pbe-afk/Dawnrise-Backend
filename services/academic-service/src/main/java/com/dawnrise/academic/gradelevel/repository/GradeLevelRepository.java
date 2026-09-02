package com.dawnrise.academic.gradelevel.repository;

import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeLevelRepository
        extends JpaRepository<GradeLevel, Long> {

    Optional<GradeLevel>
    findByIdAndAcademicYearIdAndOrganizationId(
            Long id,
            Long academicYearId,
            Long organizationId
    );

    List<GradeLevel>
    findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAsc(
            Long organizationId,
            Long academicYearId
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