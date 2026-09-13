package com.dawnrise.academic.studentattendance.offlinesync.repository;

import com.dawnrise.academic.studentattendance.offlinesync.entity.StudentAttendanceOfflineSyncOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentAttendanceOfflineSyncOperationRepository
        extends JpaRepository<StudentAttendanceOfflineSyncOperation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT operation
        FROM StudentAttendanceOfflineSyncOperation operation
        WHERE operation.organizationId = :organizationId
          AND operation.actorUserId = :actorUserId
          AND operation.idempotencyKey = :idempotencyKey
        """)
    Optional<StudentAttendanceOfflineSyncOperation> findByActorKeyForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("actorUserId") Long actorUserId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT operation
        FROM StudentAttendanceOfflineSyncOperation operation
        WHERE operation.id = :id
          AND operation.organizationId = :organizationId
          AND operation.actorUserId = :actorUserId
        """)
    Optional<StudentAttendanceOfflineSyncOperation> findForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId,
            @Param("actorUserId") Long actorUserId
    );
}
