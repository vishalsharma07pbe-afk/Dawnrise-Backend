package com.dawnrise.academic.studentattendance.importing.repository;

import com.dawnrise.academic.studentattendance.importing.entity.StudentAttendanceImportPreview;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentAttendanceImportPreviewRepository
        extends JpaRepository<StudentAttendanceImportPreview, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT preview
        FROM StudentAttendanceImportPreview preview
        WHERE preview.id = :id
          AND preview.organizationId = :organizationId
          AND preview.actorUserId = :actorUserId
        """)
    Optional<StudentAttendanceImportPreview> findForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId,
            @Param("actorUserId") Long actorUserId
    );
}
