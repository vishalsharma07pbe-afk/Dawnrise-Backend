package com.dawnrise.academic.section.repository;

import com.dawnrise.academic.section.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionRepository
        extends JpaRepository<Section, Long> {

    Optional<Section>
    findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
            Long sectionId,
            Long gradeLevelId,
            Long academicYearId,
            Long organizationId
    );

    List<Section>
    findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdOrderByDisplayOrderAsc(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCase(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            String code
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCase(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            String name
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrder(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Integer displayOrder
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCaseAndIdNot(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            String code,
            Long sectionId
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCaseAndIdNot(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            String name,
            Long sectionId
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrderAndIdNot(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Integer displayOrder,
            Long sectionId
    );
}