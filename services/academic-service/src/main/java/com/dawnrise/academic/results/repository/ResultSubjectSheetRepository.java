package com.dawnrise.academic.results.repository;

import com.dawnrise.academic.results.entity.ResultSubjectSheet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ResultSubjectSheetRepository
        extends JpaRepository<ResultSubjectSheet, Long> {

    Optional<ResultSubjectSheet>
    findByOrganizationIdAndAcademicYearIdAndExaminationIdAndScheduledAssessmentIdAndSectionId(
            Long organizationId,
            Long academicYearId,
            Long examinationId,
            Long scheduledAssessmentId,
            Long sectionId
    );

    Optional<ResultSubjectSheet> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    List<ResultSubjectSheet>
    findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(
            Long organizationId, Long academicYearId, Long examinationId,
            Long gradeLevelId, Long sectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT sheet FROM ResultSubjectSheet sheet
            WHERE sheet.organizationId=:organizationId AND sheet.academicYearId=:academicYearId
              AND sheet.examinationId=:examinationId AND sheet.gradeLevelId=:gradeLevelId
              AND sheet.sectionId=:sectionId ORDER BY sheet.id ASC
            """)
    List<ResultSubjectSheet> findScopeForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("examinationId") Long examinationId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT sheet
            FROM ResultSubjectSheet sheet
            WHERE sheet.id = :id
              AND sheet.organizationId = :organizationId
            """)
    Optional<ResultSubjectSheet> findByIdAndOrganizationIdForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId
    );
}
