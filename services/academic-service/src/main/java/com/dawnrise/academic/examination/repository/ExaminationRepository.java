package com.dawnrise.academic.examination.repository;

import com.dawnrise.academic.examination.entity.Examination;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExaminationRepository extends JpaRepository<Examination, Long> {
    Optional<Examination> findByIdAndAcademicYearIdAndOrganizationId(
            Long id, Long academicYearId, Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM Examination e
            WHERE e.id = :id AND e.academicYearId = :yearId
              AND e.organizationId = :organizationId
            """)
    Optional<Examination> findForUpdate(@Param("id") Long id,
                                        @Param("yearId") Long academicYearId,
                                        @Param("organizationId") Long organizationId);

    List<Examination> findAllByOrganizationIdAndAcademicYearIdOrderByIdAsc(
            Long organizationId, Long academicYearId);

    boolean existsByOrganizationIdAndAcademicYearIdAndNameIgnoreCase(
            Long organizationId, Long academicYearId, String name);

    boolean existsByOrganizationIdAndAcademicYearIdAndNameIgnoreCaseAndIdNot(
            Long organizationId, Long academicYearId, String name, Long id);
}
