package com.dawnrise.academic.academicyearrollover.repository;

import com.dawnrise.academic.academicyearrollover.entity.AcademicYearStructureRolloverOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AcademicYearStructureRolloverOperationRepository
        extends JpaRepository<AcademicYearStructureRolloverOperation, Long> {

    Optional<AcademicYearStructureRolloverOperation>
    findByOrganizationIdAndIdempotencyKey(
            Long organizationId,
            String idempotencyKey
    );

    Optional<AcademicYearStructureRolloverOperation>
    findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT operation
    FROM AcademicYearStructureRolloverOperation operation
    WHERE operation.id = :id
      AND operation.organizationId = :organizationId
    """)
    Optional<AcademicYearStructureRolloverOperation> findByIdAndOrganizationIdForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId
    );
}
