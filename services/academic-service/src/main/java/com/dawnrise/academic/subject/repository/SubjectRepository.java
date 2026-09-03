package com.dawnrise.academic.subject.repository;

import com.dawnrise.academic.subject.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository
        extends JpaRepository<Subject, Long> {

    Optional<Subject> findByIdAndAcademicYearIdAndOrganizationId(
            Long subjectId,
            Long academicYearId,
            Long organizationId
    );

    List<Subject> findAllByOrganizationIdAndAcademicYearIdOrderByNameAsc(
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

    boolean existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot(
            Long academicYearId,
            String code,
            Long subjectId
    );

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(
            Long academicYearId,
            String name,
            Long subjectId
    );
}