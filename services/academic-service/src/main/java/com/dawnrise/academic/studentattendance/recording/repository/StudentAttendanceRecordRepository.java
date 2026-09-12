package com.dawnrise.academic.studentattendance.recording.repository;

import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StudentAttendanceRecordRepository
        extends JpaRepository<StudentAttendanceRecord, Long> {

    List<StudentAttendanceRecord> findAllByAttendanceSessionIdOrderByIdAsc(Long attendanceSessionId);

    List<StudentAttendanceRecord> findAllByAttendanceSessionIdAndStudentEnrollmentIdIn(
            Long attendanceSessionId,
            Collection<Long> studentEnrollmentIds
    );

    List<StudentAttendanceRecord> findAllByOrganizationIdAndAttendanceSessionIdAndIdIn(
            Long organizationId,
            Long attendanceSessionId,
            Collection<Long> ids
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT record
        FROM StudentAttendanceRecord record
        WHERE record.organizationId = :organizationId
          AND record.attendanceSessionId = :attendanceSessionId
          AND record.id IN :ids
        ORDER BY record.id ASC
        """)
    List<StudentAttendanceRecord> findAllByOrganizationIdAndAttendanceSessionIdAndIdInForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("attendanceSessionId") Long attendanceSessionId,
            @Param("ids") Collection<Long> ids
    );

    long countByAttendanceSessionId(Long attendanceSessionId);

    void deleteByAttendanceSessionIdAndStudentEnrollmentIdNotIn(
            Long attendanceSessionId,
            Collection<Long> studentEnrollmentIds
    );

    @Query("""
        SELECT COUNT(record)
        FROM StudentAttendanceRecord record
        JOIN StudentAttendanceSession session
          ON session.id = record.attendanceSessionId
        WHERE record.organizationId = :organizationId
          AND record.academicYearId = :academicYearId
          AND record.studentEnrollmentId = :studentEnrollmentId
          AND record.recordedStatus = :status
          AND session.attendanceDate BETWEEN :startDate AND :endDate
          AND session.attendanceDate < :attendanceDate
        """)
    long countPriorRecordedStatus(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("studentEnrollmentId") Long studentEnrollmentId,
            @Param("status") AttendanceStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("attendanceDate") LocalDate attendanceDate
    );
}
