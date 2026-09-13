package com.dawnrise.academic.studentattendance.recording.repository;

import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface StudentAttendanceSessionRepository
        extends JpaRepository<StudentAttendanceSession, Long> {

    Optional<StudentAttendanceSession> findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate(
            Long organizationId,
            Long academicYearId,
            Long sectionId,
            LocalDate attendanceDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT session
        FROM StudentAttendanceSession session
        WHERE session.organizationId = :organizationId
          AND session.academicYearId = :academicYearId
          AND session.sectionId = :sectionId
          AND session.attendanceDate = :attendanceDate
        """)
    Optional<StudentAttendanceSession> findByContextForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("sectionId") Long sectionId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    Optional<StudentAttendanceSession> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT session
        FROM StudentAttendanceSession session
        WHERE session.id = :id
          AND session.organizationId = :organizationId
        """)
    Optional<StudentAttendanceSession> findByIdAndOrganizationIdForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId
    );

    @Query("""
        SELECT session.id AS id, session.organizationId AS organizationId
        FROM StudentAttendanceSession session
        WHERE session.lifecycleStatus = com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus.DRAFT
          AND session.updatedAt <= :now
        ORDER BY session.updatedAt ASC, session.id ASC
        """)
    List<AutomaticSubmissionCandidate> findAutomaticSubmissionCandidates(
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT session
        FROM StudentAttendanceSession session
        WHERE session.id = :sessionId
        """)
    Optional<StudentAttendanceSession> findByIdForAutomaticSubmissionUpdate(
            @Param("sessionId") Long sessionId
    );

    interface AutomaticSubmissionCandidate {
        Long getId();
        Long getOrganizationId();
    }
}
