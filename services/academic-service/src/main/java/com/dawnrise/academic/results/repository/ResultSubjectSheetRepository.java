package com.dawnrise.academic.results.repository;

import com.dawnrise.academic.results.entity.ResultSubjectSheet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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
