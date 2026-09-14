package com.dawnrise.academic.studentattendance.reporting.repository;

import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.reporting.projection.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Slice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface StudentAttendanceReportRepository
        extends Repository<StudentAttendanceRecord, Long> {

    /*
     * Returns the official submitted attendance rows for one section
     * and one calendar date.
     */
    @Query(
            value = """
                    SELECT
                        attendance_record.id
                            AS "attendanceRecordId",
                        attendance_record.student_enrollment_id
                            AS "studentEnrollmentId",
                        attendance_record.student_user_id
                            AS "studentUserId",
                        enrollment.roll_number
                            AS "rollNumber",
                        attendance_record.recorded_status
                            AS "recordedStatus",
                        attendance_record.effective_status
                            AS "effectiveStatus",
                        attendance_record.earned_credit
                            AS "earnedCredit",
                        attendance_record.possible_credit
                            AS "possibleCredit",
                        attendance_record.late_penalty_applied
                            AS "latePenaltyApplied",
                        attendance_record.remarks
                            AS "remarks"
                    FROM student_attendance_records attendance_record
                    JOIN student_attendance_sessions attendance_session
                      ON attendance_session.id =
                         attendance_record.attendance_session_id
                     AND attendance_session.organization_id =
                         attendance_record.organization_id
                     AND attendance_session.academic_year_id =
                         attendance_record.academic_year_id
                     AND attendance_session.grade_level_id =
                         attendance_record.grade_level_id
                     AND attendance_session.section_id =
                         attendance_record.section_id
                    JOIN student_enrollments enrollment
                      ON enrollment.id =
                         attendance_record.student_enrollment_id
                     AND enrollment.organization_id =
                         attendance_record.organization_id
                     AND enrollment.academic_year_id =
                         attendance_record.academic_year_id
                     AND enrollment.grade_level_id =
                         attendance_record.grade_level_id
                     AND enrollment.section_id =
                         attendance_record.section_id
                     AND enrollment.student_user_id =
                         attendance_record.student_user_id
                    WHERE attendance_record.organization_id =
                          :organizationId
                      AND attendance_record.academic_year_id =
                          :academicYearId
                      AND attendance_record.grade_level_id =
                          :gradeLevelId
                      AND attendance_record.section_id =
                          :sectionId
                      AND attendance_session.attendance_date =
                          :attendanceDate
                      AND attendance_session.lifecycle_status =
                          'SUBMITTED'
                    ORDER BY
                        enrollment.roll_number ASC,
                        attendance_record.id ASC
                    """,
            nativeQuery = true
    )
    List<DailyStudentAttendanceProjection> findDailySectionRecords(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    /*
     * Aggregates official attendance for one section and date range.
     */
    @Query(
            value = """
                    SELECT
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'PRESENT'
                        ) AS "presentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'ABSENT'
                        ) AS "absentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'LATE'
                        ) AS "lateCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'HALF_DAY'
                        ) AS "halfDayCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'EXCUSED'
                        ) AS "excusedCount",
                        COALESCE(
                            SUM(attendance_record.earned_credit),
                            0.00
                        ) AS "earnedCredit",
                        COALESCE(
                            SUM(attendance_record.possible_credit),
                            0.00
                        ) AS "possibleCredit",
                        COUNT(
                            DISTINCT attendance_session.id
                        ) AS "submittedSessionCount",
                        COUNT(
                            DISTINCT attendance_record.student_user_id
                        ) AS "distinctStudentCount"
                    FROM student_attendance_records attendance_record
                    JOIN student_attendance_sessions attendance_session
                      ON attendance_session.id =
                         attendance_record.attendance_session_id
                     AND attendance_session.organization_id =
                         attendance_record.organization_id
                     AND attendance_session.academic_year_id =
                         attendance_record.academic_year_id
                     AND attendance_session.grade_level_id =
                         attendance_record.grade_level_id
                     AND attendance_session.section_id =
                         attendance_record.section_id
                    WHERE attendance_record.organization_id =
                          :organizationId
                      AND attendance_record.academic_year_id =
                          :academicYearId
                      AND attendance_record.grade_level_id =
                          :gradeLevelId
                      AND attendance_record.section_id =
                          :sectionId
                      AND attendance_session.lifecycle_status =
                          'SUBMITTED'
                      AND attendance_session.attendance_date
                          BETWEEN :fromDate AND :toDate
                    """,
            nativeQuery = true
    )
    AttendanceAggregateProjection summarizeSection(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /*
     * Aggregates official attendance across all sections in one grade.
     */
    @Query(
            value = """
                    SELECT
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'PRESENT'
                        ) AS "presentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'ABSENT'
                        ) AS "absentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'LATE'
                        ) AS "lateCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'HALF_DAY'
                        ) AS "halfDayCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'EXCUSED'
                        ) AS "excusedCount",
                        COALESCE(
                            SUM(attendance_record.earned_credit),
                            0.00
                        ) AS "earnedCredit",
                        COALESCE(
                            SUM(attendance_record.possible_credit),
                            0.00
                        ) AS "possibleCredit",
                        COUNT(
                            DISTINCT attendance_session.id
                        ) AS "submittedSessionCount",
                        COUNT(
                            DISTINCT attendance_record.student_user_id
                        ) AS "distinctStudentCount"
                    FROM student_attendance_records attendance_record
                    JOIN student_attendance_sessions attendance_session
                      ON attendance_session.id =
                         attendance_record.attendance_session_id
                     AND attendance_session.organization_id =
                         attendance_record.organization_id
                     AND attendance_session.academic_year_id =
                         attendance_record.academic_year_id
                     AND attendance_session.grade_level_id =
                         attendance_record.grade_level_id
                     AND attendance_session.section_id =
                         attendance_record.section_id
                    WHERE attendance_record.organization_id =
                          :organizationId
                      AND attendance_record.academic_year_id =
                          :academicYearId
                      AND attendance_record.grade_level_id =
                          :gradeLevelId
                      AND attendance_session.lifecycle_status =
                          'SUBMITTED'
                      AND attendance_session.attendance_date
                          BETWEEN :fromDate AND :toDate
                    """,
            nativeQuery = true
    )
    AttendanceAggregateProjection summarizeGrade(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /*
     * Aggregates one student's official attendance for a date range.
     */
    @Query(
            value = """
                    SELECT
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'PRESENT'
                        ) AS "presentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'ABSENT'
                        ) AS "absentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'LATE'
                        ) AS "lateCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'HALF_DAY'
                        ) AS "halfDayCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'EXCUSED'
                        ) AS "excusedCount",
                        COALESCE(
                            SUM(attendance_record.earned_credit),
                            0.00
                        ) AS "earnedCredit",
                        COALESCE(
                            SUM(attendance_record.possible_credit),
                            0.00
                        ) AS "possibleCredit",
                        COUNT(
                            DISTINCT attendance_session.id
                        ) AS "submittedSessionCount",
                        COUNT(
                            DISTINCT attendance_record.student_user_id
                        ) AS "distinctStudentCount"
                    FROM student_attendance_records attendance_record
                    JOIN student_attendance_sessions attendance_session
                      ON attendance_session.id =
                         attendance_record.attendance_session_id
                     AND attendance_session.organization_id =
                         attendance_record.organization_id
                     AND attendance_session.academic_year_id =
                         attendance_record.academic_year_id
                     AND attendance_session.grade_level_id =
                         attendance_record.grade_level_id
                     AND attendance_session.section_id =
                         attendance_record.section_id
                    WHERE attendance_record.organization_id =
                          :organizationId
                      AND attendance_record.academic_year_id =
                          :academicYearId
                      AND attendance_record.student_user_id =
                          :studentUserId
                      AND attendance_record.grade_level_id =
                          :gradeLevelId
                      AND attendance_record.section_id =
                          :sectionId
                      AND attendance_session.lifecycle_status =
                          'SUBMITTED'
                      AND attendance_session.attendance_date
                          BETWEEN :fromDate AND :toDate
                    """,
            nativeQuery = true
    )
    AttendanceAggregateProjection summarizeStudent(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("studentUserId") Long studentUserId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /*
     * Returns paginated official attendance history for one student.
     */
    @Query(
            value = """
                    SELECT
                        attendance_session.id
                            AS "attendanceSessionId",
                        attendance_record.id
                            AS "attendanceRecordId",
                        attendance_record.student_enrollment_id
                            AS "studentEnrollmentId",
                        attendance_record.academic_year_id
                            AS "academicYearId",
                        attendance_record.grade_level_id
                            AS "gradeLevelId",
                        attendance_record.section_id
                            AS "sectionId",
                        attendance_session.attendance_date
                            AS "attendanceDate",
                        attendance_record.recorded_status
                            AS "recordedStatus",
                        attendance_record.effective_status
                            AS "effectiveStatus",
                        attendance_record.earned_credit
                            AS "earnedCredit",
                        attendance_record.possible_credit
                            AS "possibleCredit",
                        attendance_record.late_penalty_applied
                            AS "latePenaltyApplied",
                        attendance_record.remarks
                            AS "remarks",
                        attendance_session.submission_type
                            AS "submissionType",
                        attendance_session.submitted_at
                            AS "submittedAt",
                        attendance_record.updated_at
                            AS "lastUpdatedAt"
                    FROM student_attendance_records attendance_record
                    JOIN student_attendance_sessions attendance_session
                      ON attendance_session.id =
                         attendance_record.attendance_session_id
                     AND attendance_session.organization_id =
                         attendance_record.organization_id
                     AND attendance_session.academic_year_id =
                         attendance_record.academic_year_id
                     AND attendance_session.grade_level_id =
                         attendance_record.grade_level_id
                     AND attendance_session.section_id =
                         attendance_record.section_id
                    WHERE attendance_record.organization_id =
                          :organizationId
                      AND attendance_record.academic_year_id =
                          :academicYearId
                      AND attendance_record.student_user_id =
                          :studentUserId
                      AND attendance_record.grade_level_id =
                          :gradeLevelId
                      AND attendance_record.section_id =
                          :sectionId
                      AND attendance_session.lifecycle_status =
                          'SUBMITTED'
                      AND attendance_session.attendance_date
                          BETWEEN :fromDate AND :toDate
                    ORDER BY
                        attendance_session.attendance_date DESC,
                        attendance_record.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM student_attendance_records attendance_record
                    JOIN student_attendance_sessions attendance_session
                      ON attendance_session.id =
                         attendance_record.attendance_session_id
                     AND attendance_session.organization_id =
                         attendance_record.organization_id
                     AND attendance_session.academic_year_id =
                         attendance_record.academic_year_id
                     AND attendance_session.grade_level_id =
                         attendance_record.grade_level_id
                     AND attendance_session.section_id =
                         attendance_record.section_id
                    WHERE attendance_record.organization_id =
                          :organizationId
                      AND attendance_record.academic_year_id =
                          :academicYearId
                      AND attendance_record.student_user_id =
                          :studentUserId
                      AND attendance_record.grade_level_id =
                          :gradeLevelId
                      AND attendance_record.section_id =
                          :sectionId
                      AND attendance_session.lifecycle_status =
                          'SUBMITTED'
                      AND attendance_session.attendance_date
                          BETWEEN :fromDate AND :toDate
                    """,
            nativeQuery = true
    )
    Page<StudentAttendanceHistoryProjection> findStudentHistory(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("studentUserId") Long studentUserId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    /*
     * Returns one student's official monthly totals.
     */
    @Query(
            value = """
                    SELECT
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'PRESENT'
                        ) AS "presentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'ABSENT'
                        ) AS "absentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'LATE'
                        ) AS "lateCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'HALF_DAY'
                        ) AS "halfDayCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'EXCUSED'
                        ) AS "excusedCount",
                        COALESCE(
                            SUM(attendance_record.earned_credit),
                            0.00
                        ) AS "earnedCredit",
                        COALESCE(
                            SUM(attendance_record.possible_credit),
                            0.00
                        ) AS "possibleCredit",
                        COUNT(
                            DISTINCT attendance_session.attendance_date
                        ) AS "submittedAttendanceDays"
                    FROM student_attendance_records attendance_record
                    JOIN student_attendance_sessions attendance_session
                      ON attendance_session.id =
                         attendance_record.attendance_session_id
                     AND attendance_session.organization_id =
                         attendance_record.organization_id
                     AND attendance_session.academic_year_id =
                         attendance_record.academic_year_id
                     AND attendance_session.grade_level_id =
                         attendance_record.grade_level_id
                     AND attendance_session.section_id =
                         attendance_record.section_id
                    WHERE attendance_record.organization_id =
                          :organizationId
                      AND attendance_record.academic_year_id =
                          :academicYearId
                      AND attendance_record.student_user_id =
                          :studentUserId
                      AND attendance_record.grade_level_id =
                          :gradeLevelId
                      AND attendance_record.section_id =
                          :sectionId
                      AND attendance_session.lifecycle_status =
                          'SUBMITTED'
                      AND attendance_session.attendance_date
                          BETWEEN :monthStart AND :monthEnd
                    """,
            nativeQuery = true
    )
    StudentMonthlyAttendanceProjection summarizeStudentMonth(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("studentUserId") Long studentUserId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd
    );

    /*
     * Returns one aggregate row for every section in a grade.
     * Sections without submitted attendance are also returned.
     */
    @Query(
            value = """
                    SELECT
                        section.id
                            AS "sectionId",
                        section.code
                            AS "sectionCode",
                        section.name
                            AS "sectionName",
                        COUNT(attendance_record.id) FILTER (
                            WHERE attendance_record.effective_status =
                                  'PRESENT'
                        ) AS "presentCount",
                        COUNT(attendance_record.id) FILTER (
                            WHERE attendance_record.effective_status =
                                  'ABSENT'
                        ) AS "absentCount",
                        COUNT(attendance_record.id) FILTER (
                            WHERE attendance_record.effective_status =
                                  'LATE'
                        ) AS "lateCount",
                        COUNT(attendance_record.id) FILTER (
                            WHERE attendance_record.effective_status =
                                  'HALF_DAY'
                        ) AS "halfDayCount",
                        COUNT(attendance_record.id) FILTER (
                            WHERE attendance_record.effective_status =
                                  'EXCUSED'
                        ) AS "excusedCount",
                        COALESCE(
                            SUM(attendance_record.earned_credit),
                            0.00
                        ) AS "earnedCredit",
                        COALESCE(
                            SUM(attendance_record.possible_credit),
                            0.00
                        ) AS "possibleCredit",
                        COUNT(
                            DISTINCT attendance_session.id
                        ) AS "submittedSessionCount",
                        COUNT(
                            DISTINCT attendance_record.student_user_id
                        ) AS "distinctStudentCount"
                    FROM sections section
                    LEFT JOIN student_attendance_sessions
                              attendance_session
                      ON attendance_session.organization_id =
                         section.organization_id
                     AND attendance_session.academic_year_id =
                         section.academic_year_id
                     AND attendance_session.grade_level_id =
                         section.grade_level_id
                     AND attendance_session.section_id =
                         section.id
                     AND attendance_session.lifecycle_status =
                         'SUBMITTED'
                     AND attendance_session.attendance_date
                         BETWEEN :fromDate AND :toDate
                    LEFT JOIN student_attendance_records
                              attendance_record
                      ON attendance_record.organization_id =
                         attendance_session.organization_id
                     AND attendance_record.academic_year_id =
                         attendance_session.academic_year_id
                     AND attendance_record.grade_level_id =
                         attendance_session.grade_level_id
                     AND attendance_record.section_id =
                         attendance_session.section_id
                     AND attendance_record.attendance_session_id =
                         attendance_session.id
                    WHERE section.organization_id =
                          :organizationId
                      AND section.academic_year_id =
                          :academicYearId
                      AND section.grade_level_id =
                          :gradeLevelId
                    GROUP BY
                        section.id,
                        section.code,
                        section.name,
                        section.display_order
                    ORDER BY
                        section.display_order ASC,
                        section.id ASC
                    """,
            nativeQuery = true
    )
    List<GradeSectionAttendanceAggregateProjection>
    summarizeGradeSections(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /*
     * Returns students whose official attendance percentage is below
     * the requested threshold. Records with zero possible credit do
     * not independently produce a percentage.
     */
    @Query(
            value = """
                    SELECT
                        enrollment.id
                            AS "studentEnrollmentId",
                        enrollment.student_user_id
                            AS "studentUserId",
                        enrollment.grade_level_id
                            AS "gradeLevelId",
                        enrollment.section_id
                            AS "sectionId",
                        enrollment.roll_number
                            AS "rollNumber",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'PRESENT'
                        ) AS "presentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'ABSENT'
                        ) AS "absentCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'LATE'
                        ) AS "lateCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'HALF_DAY'
                        ) AS "halfDayCount",
                        COUNT(*) FILTER (
                            WHERE attendance_record.effective_status =
                                  'EXCUSED'
                        ) AS "excusedCount",
                        COALESCE(
                            SUM(attendance_record.earned_credit),
                            0.00
                        ) AS "earnedCredit",
                        COALESCE(
                            SUM(attendance_record.possible_credit),
                            0.00
                        ) AS "possibleCredit",
                        ROUND(
                            (
                                SUM(attendance_record.earned_credit)
                                * 100.00
                            )
                            / NULLIF(
                                SUM(
                                    attendance_record.possible_credit
                                ),
                                0.00
                            ),
                            2
                        ) AS "attendancePercentage"
                    FROM student_enrollments enrollment
                    JOIN student_attendance_records attendance_record
                      ON attendance_record.organization_id =
                         enrollment.organization_id
                     AND attendance_record.academic_year_id =
                         enrollment.academic_year_id
                     AND attendance_record.student_enrollment_id =
                         enrollment.id
                     AND attendance_record.student_user_id =
                         enrollment.student_user_id
                     AND attendance_record.grade_level_id =
                         enrollment.grade_level_id
                     AND attendance_record.section_id =
                         enrollment.section_id
                    JOIN student_attendance_sessions attendance_session
                      ON attendance_session.id =
                         attendance_record.attendance_session_id
                     AND attendance_session.organization_id =
                         attendance_record.organization_id
                     AND attendance_session.academic_year_id =
                         attendance_record.academic_year_id
                     AND attendance_session.grade_level_id =
                         attendance_record.grade_level_id
                     AND attendance_session.section_id =
                         attendance_record.section_id
                    WHERE enrollment.organization_id =
                          :organizationId
                      AND enrollment.academic_year_id =
                          :academicYearId
                      AND (
                          :gradeLevelId IS NULL
                          OR enrollment.grade_level_id =
                             :gradeLevelId
                      )
                      AND (
                          :sectionId IS NULL
                          OR enrollment.section_id =
                             :sectionId
                      )
                      AND attendance_session.lifecycle_status =
                          'SUBMITTED'
                      AND attendance_session.attendance_date
                          BETWEEN :fromDate AND :toDate
                    GROUP BY
                        enrollment.id,
                        enrollment.student_user_id,
                        enrollment.grade_level_id,
                        enrollment.section_id,
                        enrollment.roll_number
                    HAVING
                        SUM(attendance_record.possible_credit) > 0
                        AND (
                            SUM(attendance_record.earned_credit)
                            * 100.00
                        )
                        / SUM(attendance_record.possible_credit)
                        < :thresholdPercentage
                    ORDER BY
                        "attendancePercentage" ASC,
                        enrollment.roll_number ASC,
                        enrollment.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT enrollment.id
                        FROM student_enrollments enrollment
                        JOIN student_attendance_records
                                  attendance_record
                          ON attendance_record.organization_id =
                             enrollment.organization_id
                         AND attendance_record.academic_year_id =
                             enrollment.academic_year_id
                         AND attendance_record.student_enrollment_id =
                             enrollment.id
                         AND attendance_record.student_user_id =
                             enrollment.student_user_id
                         AND attendance_record.grade_level_id =
                             enrollment.grade_level_id
                         AND attendance_record.section_id =
                             enrollment.section_id
                        JOIN student_attendance_sessions
                                  attendance_session
                          ON attendance_session.id =
                             attendance_record.attendance_session_id
                         AND attendance_session.organization_id =
                             attendance_record.organization_id
                         AND attendance_session.academic_year_id =
                             attendance_record.academic_year_id
                         AND attendance_session.grade_level_id =
                             attendance_record.grade_level_id
                         AND attendance_session.section_id =
                             attendance_record.section_id
                        WHERE enrollment.organization_id =
                              :organizationId
                          AND enrollment.academic_year_id =
                              :academicYearId
                          AND (
                              :gradeLevelId IS NULL
                              OR enrollment.grade_level_id =
                                 :gradeLevelId
                          )
                          AND (
                              :sectionId IS NULL
                              OR enrollment.section_id =
                                 :sectionId
                          )
                          AND attendance_session.lifecycle_status =
                              'SUBMITTED'
                          AND attendance_session.attendance_date
                              BETWEEN :fromDate AND :toDate
                        GROUP BY enrollment.id
                        HAVING
                            SUM(
                                attendance_record.possible_credit
                            ) > 0
                            AND (
                                SUM(
                                    attendance_record.earned_credit
                                ) * 100.00
                            )
                            / SUM(
                                attendance_record.possible_credit
                            ) < :thresholdPercentage
                    ) low_attendance_students
                    """,
            nativeQuery = true
    )
    Page<LowAttendanceStudentProjection>
    findLowAttendanceStudents(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("thresholdPercentage")
            BigDecimal thresholdPercentage,
            Pageable pageable
    );

    /*
     * Returns required calendar-day/section combinations where:
     * - no session exists,
     * - the session is still a draft, or
     * - the recorded roster is incomplete.
     */
    @Query(
            value = """
                    SELECT *
                    FROM (
                        SELECT
                            calendar_day.id
                                AS "academicCalendarDayId",
                            calendar_day.calendar_date
                                AS "attendanceDate",
                            section.grade_level_id
                                AS "gradeLevelId",
                            section.id
                                AS "sectionId",
                            section.code
                                AS "sectionCode",
                            section.name
                                AS "sectionName",
                            attendance_session.id
                                AS "attendanceSessionId",
                            attendance_session.lifecycle_status
                                AS "lifecycleStatus",
                            (
                                SELECT COUNT(*)
                                FROM student_enrollments enrollment
                                WHERE enrollment.organization_id =
                                      calendar_day.organization_id
                                  AND enrollment.academic_year_id =
                                      calendar_day.academic_year_id
                                  AND enrollment.grade_level_id =
                                      section.grade_level_id
                                  AND enrollment.section_id =
                                      section.id
                                  AND enrollment.enrolled_on <=
                                      calendar_day.calendar_date
                                  AND (
                                      enrollment.ended_on IS NULL
                                      OR enrollment.ended_on >=
                                         calendar_day.calendar_date
                                  )
                            ) AS "eligibleStudentCount",
                            (
                                SELECT COUNT(*)
                                FROM student_attendance_records
                                          attendance_record
                                JOIN student_enrollments enrollment
                                  ON enrollment.id =
                                     attendance_record.student_enrollment_id
                                 AND enrollment.organization_id =
                                     attendance_record.organization_id
                                 AND enrollment.academic_year_id =
                                     attendance_record.academic_year_id
                                 AND enrollment.grade_level_id =
                                     attendance_record.grade_level_id
                                 AND enrollment.section_id =
                                     attendance_record.section_id
                                 AND enrollment.student_user_id =
                                     attendance_record.student_user_id
                                 AND enrollment.enrolled_on <=
                                     calendar_day.calendar_date
                                 AND (
                                     enrollment.ended_on IS NULL
                                     OR enrollment.ended_on >=
                                        calendar_day.calendar_date
                                 )
                                WHERE attendance_session.id IS NOT NULL
                                  AND attendance_record.organization_id =
                                      calendar_day.organization_id
                                  AND attendance_record.academic_year_id =
                                      calendar_day.academic_year_id
                                  AND attendance_record.grade_level_id =
                                      section.grade_level_id
                                  AND attendance_record.section_id =
                                      section.id
                                  AND attendance_record.attendance_session_id =
                                      attendance_session.id
                            ) AS "recordedStudentCount",
                            section.display_order
                                AS section_display_order
                        FROM academic_calendar_days calendar_day
                        CROSS JOIN sections section
                        LEFT JOIN student_attendance_sessions
                                  attendance_session
                          ON attendance_session.organization_id =
                             calendar_day.organization_id
                         AND attendance_session.academic_year_id =
                             calendar_day.academic_year_id
                         AND attendance_session.grade_level_id =
                             section.grade_level_id
                         AND attendance_session.section_id =
                             section.id
                         AND attendance_session.academic_calendar_day_id =
                             calendar_day.id
                         AND attendance_session.attendance_date =
                             calendar_day.calendar_date
                        WHERE calendar_day.organization_id =
                              :organizationId
                          AND calendar_day.academic_year_id =
                              :academicYearId
                          AND section.organization_id =
                              calendar_day.organization_id
                          AND section.academic_year_id =
                              calendar_day.academic_year_id
                          AND (
                              :gradeLevelId IS NULL
                              OR section.grade_level_id =
                                 :gradeLevelId
                          )
                          AND (
                              :sectionId IS NULL
                              OR section.id = :sectionId
                          )
                          AND calendar_day.attendance_requirement =
                              'REQUIRED'
                          AND calendar_day.counts_toward_percentage =
                              TRUE
                          AND calendar_day.calendar_date
                              BETWEEN :fromDate AND :toDate
                    ) report_row
                    WHERE report_row."attendanceSessionId" IS NULL
                       OR report_row."lifecycleStatus" <> 'SUBMITTED'
                       OR report_row."recordedStudentCount"
                          <> report_row."eligibleStudentCount"
                    ORDER BY
                        report_row."attendanceDate" ASC,
                        report_row.section_display_order ASC,
                        report_row."sectionId" ASC
                    """,
            nativeQuery = true
    )
    Slice<MissingAttendanceSessionProjection>
    findMissingOrIncompleteSessions(
            @Param("organizationId") Long organizationId,
            @Param("academicYearId") Long academicYearId,
            @Param("gradeLevelId") Long gradeLevelId,
            @Param("sectionId") Long sectionId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}
