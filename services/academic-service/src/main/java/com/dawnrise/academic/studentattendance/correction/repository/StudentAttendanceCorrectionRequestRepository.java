package com.dawnrise.academic.studentattendance.correction.repository;

import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionRequest;
import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentAttendanceCorrectionRequestRepository
        extends JpaRepository<StudentAttendanceCorrectionRequest, Long> {

    Optional<StudentAttendanceCorrectionRequest> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndAttendanceSessionIdAndStatus(
            Long organizationId,
            Long attendanceSessionId,
            StudentAttendanceCorrectionStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM StudentAttendanceCorrectionRequest request
        WHERE request.id = :id
          AND request.organizationId = :organizationId
        """)
    Optional<StudentAttendanceCorrectionRequest> findByIdAndOrganizationIdForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId
    );

    @Query("""
        SELECT request
        FROM StudentAttendanceCorrectionRequest request
        WHERE request.organizationId = :organizationId
          AND (:attendanceSessionId IS NULL OR request.attendanceSessionId = :attendanceSessionId)
          AND (:status IS NULL OR request.status = :status)
        ORDER BY request.requestedAt DESC, request.id DESC
        """)
    Page<StudentAttendanceCorrectionRequest> findTenantRequests(
            @Param("organizationId") Long organizationId,
            @Param("attendanceSessionId") Long attendanceSessionId,
            @Param("status") StudentAttendanceCorrectionStatus status,
            Pageable pageable
    );

    @Query("""
        SELECT request
        FROM StudentAttendanceCorrectionRequest request
        WHERE request.organizationId = :organizationId
          AND (:attendanceSessionId IS NULL OR request.attendanceSessionId = :attendanceSessionId)
          AND (:status IS NULL OR request.status = :status)
          AND EXISTS (
              SELECT assignment.id
              FROM TeacherAssignment assignment
              WHERE assignment.organizationId = request.organizationId
                AND assignment.academicYearId = request.academicYearId
                AND assignment.gradeLevelId = request.gradeLevelId
                AND assignment.sectionId = request.sectionId
                AND assignment.teacherUserId = :teacherUserId
          )
        ORDER BY request.requestedAt DESC, request.id DESC
        """)
    Page<StudentAttendanceCorrectionRequest> findTeacherScopedRequests(
            @Param("organizationId") Long organizationId,
            @Param("teacherUserId") Long teacherUserId,
            @Param("attendanceSessionId") Long attendanceSessionId,
            @Param("status") StudentAttendanceCorrectionStatus status,
            Pageable pageable
    );
}
