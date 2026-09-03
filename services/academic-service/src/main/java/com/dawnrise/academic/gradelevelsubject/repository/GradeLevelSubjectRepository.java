package com.dawnrise.academic.gradelevelsubject.repository;

import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeLevelSubjectRepository
        extends JpaRepository<GradeLevelSubject, Long> {

    Optional<GradeLevelSubject>
    findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
            Long assignmentId,
            Long gradeLevelId,
            Long academicYearId,
            Long organizationId
    );

    List<GradeLevelSubject>
    findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdOrderByDisplayOrderAsc(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSubjectId(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long subjectId
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrder(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Integer displayOrder
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrderAndIdNot(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Integer displayOrder,
            Long assignmentId
    );
}