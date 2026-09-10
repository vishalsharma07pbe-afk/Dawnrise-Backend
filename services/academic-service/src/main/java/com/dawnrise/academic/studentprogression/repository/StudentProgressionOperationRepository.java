package com.dawnrise.academic.studentprogression.repository;

import com.dawnrise.academic.studentprogression.entity.StudentProgressionOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentProgressionOperationRepository
        extends JpaRepository<StudentProgressionOperation, Long> {

    Optional<StudentProgressionOperation>
    findByIdAndOrganizationId(
            Long operationId,
            Long organizationId
    );

    Optional<StudentProgressionOperation>
    findByOrganizationIdAndIdempotencyKey(
            Long organizationId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT operation
            FROM StudentProgressionOperation operation
            WHERE operation.id = :operationId
              AND operation.organizationId = :organizationId
            """)
    Optional<StudentProgressionOperation>
    findByIdAndOrganizationIdForUpdate(
            @Param("operationId") Long operationId,
            @Param("organizationId") Long organizationId
    );
}