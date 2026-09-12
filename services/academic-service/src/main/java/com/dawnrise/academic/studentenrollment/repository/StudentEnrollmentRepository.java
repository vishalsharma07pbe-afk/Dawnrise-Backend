package com.dawnrise.academic.studentenrollment.repository;

import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface StudentEnrollmentRepository
        extends JpaRepository<StudentEnrollment, Long> {

    Optional<StudentEnrollment>
    findByIdAndAcademicYearIdAndOrganizationId(
            Long enrollmentId,
            Long academicYearId,
            Long organizationId
    );

    Optional<StudentEnrollment>
    findByOrganizationIdAndAcademicYearIdAndStudentUserIdAndStatus(
            Long organizationId,
            Long academicYearId,
            Long studentUserId,
            StudentEnrollmentStatus status
    );

    List<StudentEnrollment>
    findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndStatusOrderByRollNumberAsc(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            StudentEnrollmentStatus status
    );

    @Query("""
        SELECT enrollment
        FROM StudentEnrollment enrollment
        WHERE enrollment.organizationId = :organizationId
          AND enrollment.academicYearId = :academicYearId
          AND enrollment.gradeLevelId = :gradeLevelId
          AND enrollment.sectionId = :sectionId
          AND enrollment.enrolledOn <= :attendanceDate
          AND (enrollment.endedOn IS NULL OR enrollment.endedOn >= :attendanceDate)
        ORDER BY enrollment.rollNumber ASC, enrollment.id ASC
        """)
    List<StudentEnrollment> findEligibleForAttendanceDate(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    List<StudentEnrollment>
    findAllByOrganizationIdAndStudentUserIdOrderByAcademicYearIdDescEnrolledOnDesc(
            Long organizationId,
            Long studentUserId
    );

    List<StudentEnrollment>
    findAllByOrganizationIdAndAcademicYearIdAndIdIn(
            Long organizationId,
            Long academicYearId,
            Collection<Long> enrollmentIds
    );

    List<StudentEnrollment>
    findAllByOrganizationIdAndAcademicYearIdAndStudentUserIdInAndStatus(
            Long organizationId,
            Long academicYearId,
            Collection<Long> studentUserIds,
            StudentEnrollmentStatus status
    );

    List<StudentEnrollment>
    findAllByOrganizationIdAndAcademicYearIdAndSectionIdInAndStatus(
            Long organizationId,
            Long academicYearId,
            Collection<Long> sectionIds,
            StudentEnrollmentStatus status
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndStudentUserIdAndStatus(
            Long organizationId,
            Long academicYearId,
            Long studentUserId,
            StudentEnrollmentStatus status
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndSectionIdAndRollNumberIgnoreCaseAndStatus(
            Long organizationId,
            Long academicYearId,
            Long sectionId,
            String rollNumber,
            StudentEnrollmentStatus status
    );

    boolean
    existsByOrganizationIdAndAcademicYearIdAndSectionIdAndRollNumberIgnoreCaseAndStatusAndIdNot(
            Long organizationId,
            Long academicYearId,
            Long sectionId,
            String rollNumber,
            StudentEnrollmentStatus status,
            Long enrollmentId
    );

    boolean existsByOrganizationIdAndAcademicYearId(
            Long organizationId,
            Long academicYearId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT enrollment
        FROM StudentEnrollment enrollment
        WHERE enrollment.organizationId = :organizationId
          AND enrollment.academicYearId = :academicYearId
          AND enrollment.id IN :enrollmentIds
        ORDER BY enrollment.id ASC
        """)
    List<StudentEnrollment>
    findAllByOrganizationIdAndAcademicYearIdAndIdInForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("enrollmentIds") Collection<Long> enrollmentIds
    );

    @Query("""
        SELECT enrollment
        FROM StudentEnrollment enrollment
        WHERE enrollment.organizationId = :organizationId
          AND enrollment.academicYearId = :academicYearId
          AND enrollment.gradeLevelId = :gradeLevelId
          AND enrollment.sectionId = :sectionId
          AND enrollment.id IN :enrollmentIds
          AND enrollment.enrolledOn <= :attendanceDate
          AND (enrollment.endedOn IS NULL OR enrollment.endedOn >= :attendanceDate)
        """)
    List<StudentEnrollment> findEligibleForAttendanceDateByIds(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("attendanceDate") LocalDate attendanceDate,
            @Param("enrollmentIds") Collection<Long> enrollmentIds
    );
}
