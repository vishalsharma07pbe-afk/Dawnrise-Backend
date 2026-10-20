package com.dawnrise.academic.results.repository;

import com.dawnrise.academic.results.entity.ResultPublication;
import com.dawnrise.academic.results.enums.ResultPublicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResultPublicationRepository extends JpaRepository<ResultPublication, Long> {

    Optional<ResultPublication>
    findByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdAndStatus(
            Long organizationId,
            Long academicYearId,
            Long examinationId,
            Long gradeLevelId,
            Long sectionId,
            ResultPublicationStatus status
    );

    Optional<ResultPublication> findByIdAndOrganizationIdAndStatus(
            Long id,
            Long organizationId,
            ResultPublicationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ResultPublication p WHERE p.id=:id AND p.organizationId=:organizationId")
    Optional<ResultPublication> findByIdAndOrganizationIdForUpdate(@Param("id") Long id, @Param("organizationId") Long organizationId);

    @Query("""
            SELECT DISTINCT publication
            FROM ResultPublication publication
            JOIN ResultSubjectSheet sheet
              ON sheet.organizationId = publication.organizationId
             AND sheet.academicYearId = publication.academicYearId
             AND sheet.examinationId = publication.examinationId
             AND sheet.gradeLevelId = publication.gradeLevelId
             AND sheet.sectionId = publication.sectionId
            JOIN ResultSubjectMark mark
              ON mark.subjectSheetId = sheet.id
             AND mark.organizationId = publication.organizationId
             AND mark.academicYearId = publication.academicYearId
             AND mark.gradeLevelId = publication.gradeLevelId
             AND mark.sectionId = publication.sectionId
            WHERE publication.organizationId = :organizationId
              AND publication.status = com.dawnrise.academic.results.enums.ResultPublicationStatus.PUBLISHED
              AND mark.studentUserId = :studentUserId
            ORDER BY publication.publishedAt DESC, publication.id DESC
            """)
    List<ResultPublication> findAllPublishedForStudent(
            @Param("organizationId") Long organizationId,
            @Param("studentUserId") Long studentUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT publication
            FROM ResultPublication publication
            WHERE publication.organizationId = :organizationId
              AND publication.academicYearId = :academicYearId
              AND publication.examinationId = :examinationId
              AND publication.gradeLevelId = :gradeLevelId
              AND publication.sectionId = :sectionId
            """)
    Optional<ResultPublication> findScopeForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("examinationId") Long examinationId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId
    );
}
