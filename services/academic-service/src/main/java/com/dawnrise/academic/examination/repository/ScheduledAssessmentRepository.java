package com.dawnrise.academic.examination.repository;

import com.dawnrise.academic.examination.entity.ScheduledAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledAssessmentRepository
        extends JpaRepository<ScheduledAssessment, Long> {

    List<ScheduledAssessment>
    findAllByOrganizationIdAndAcademicYearIdAndExaminationIdOrderByAssessmentDateAscStartTimeAscIdAsc(
            Long organizationId, Long academicYearId, Long examinationId);

    Optional<ScheduledAssessment>
    findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
            Long id, Long examinationId, Long academicYearId, Long organizationId);

    boolean existsByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelSubjectId(
            Long organizationId, Long academicYearId, Long examinationId,
            Long gradeLevelSubjectId);

    boolean existsByOrganizationIdAndAcademicYearIdAndExaminationId(
            Long organizationId, Long academicYearId, Long examinationId);

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM ScheduledAssessment a
            WHERE a.organizationId = :organizationId
              AND a.academicYearId = :academicYearId
              AND a.examinationId = :examinationId
              AND a.gradeLevelId = :gradeLevelId
              AND a.assessmentDate = :assessmentDate
              AND a.startTime < :endTime
              AND a.endTime > :startTime
              AND (:excludeId IS NULL OR a.id <> :excludeId)
            """)
    boolean hasTimeConflict(@Param("organizationId") Long organizationId,
                            @Param("academicYearId") Long academicYearId,
                            @Param("examinationId") Long examinationId,
                            @Param("gradeLevelId") Long gradeLevelId,
                            @Param("assessmentDate") LocalDate assessmentDate,
                            @Param("startTime") LocalTime startTime,
                            @Param("endTime") LocalTime endTime,
                            @Param("excludeId") Long excludeId);
}
