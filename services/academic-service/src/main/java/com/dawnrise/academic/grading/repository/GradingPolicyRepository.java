package com.dawnrise.academic.grading.repository;

import com.dawnrise.academic.grading.entity.GradingPolicy;
import com.dawnrise.academic.grading.enums.GradingPolicyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface GradingPolicyRepository extends JpaRepository<GradingPolicy, Long> {
    List<GradingPolicy> findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdOrderByCreatedAtDesc(
            Long organizationId, Long academicYearId, Long gradeLevelId);

    Optional<GradingPolicy> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<GradingPolicy> findByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndStatus(
            Long organizationId, Long academicYearId, Long gradeLevelId, GradingPolicyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT policy FROM GradingPolicy policy
            WHERE policy.organizationId = :organizationId
              AND policy.academicYearId = :academicYearId
              AND policy.gradeLevelId = :gradeLevelId
            ORDER BY policy.id
            """)
    List<GradingPolicy> lockScope(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId);
}
