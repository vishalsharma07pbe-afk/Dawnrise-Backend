package com.dawnrise.academic.teacherassignment.repository;

import com.dawnrise.academic.teacherassignment.entity.TeacherAssignment;
import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherAssignmentRepository
        extends JpaRepository<TeacherAssignment, Long> {

    Optional<TeacherAssignment>
    findByIdAndSectionIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
            Long assignmentId,
            Long sectionId,
            Long gradeLevelId,
            Long academicYearId,
            Long organizationId
    );

    List<TeacherAssignment>
    findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdOrderByAssignmentTypeAscIdAsc(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndAssignmentType(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            TeacherAssignmentType assignmentType
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectId(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            Long gradeLevelSubjectId
    );
}