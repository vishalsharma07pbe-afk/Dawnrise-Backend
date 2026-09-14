package com.dawnrise.academic.studentattendance.reporting.mapper;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;
import com.dawnrise.academic.studentattendance.reporting.dto.*;
import com.dawnrise.academic.studentattendance.reporting.projection.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.math.BigDecimal;

@Component
public class StudentAttendanceReportMapper {

    public AttendanceSummary toSummary(
            AttendanceAggregateProjection projection
    ) {
        if (projection == null) {
            return AttendanceSummary.empty();
        }

        return AttendanceSummary.of(
                valueOrZero(projection.getPresentCount()),
                valueOrZero(projection.getAbsentCount()),
                valueOrZero(projection.getLateCount()),
                valueOrZero(projection.getHalfDayCount()),
                valueOrZero(projection.getExcusedCount()),
                valueOrZero(projection.getEarnedCredit()),
                valueOrZero(projection.getPossibleCredit())
        );
    }

    public AttendanceSummary toSummary(
            StudentMonthlyAttendanceProjection projection
    ) {
        if (projection == null) {
            return AttendanceSummary.empty();
        }

        return AttendanceSummary.of(
                valueOrZero(projection.getPresentCount()),
                valueOrZero(projection.getAbsentCount()),
                valueOrZero(projection.getLateCount()),
                valueOrZero(projection.getHalfDayCount()),
                valueOrZero(projection.getExcusedCount()),
                valueOrZero(projection.getEarnedCredit()),
                valueOrZero(projection.getPossibleCredit())
        );
    }

    public AttendanceSummary toSummary(
            GradeSectionAttendanceAggregateProjection projection
    ) {
        if (projection == null) {
            return AttendanceSummary.empty();
        }

        return AttendanceSummary.of(
                valueOrZero(projection.getPresentCount()),
                valueOrZero(projection.getAbsentCount()),
                valueOrZero(projection.getLateCount()),
                valueOrZero(projection.getHalfDayCount()),
                valueOrZero(projection.getExcusedCount()),
                valueOrZero(projection.getEarnedCredit()),
                valueOrZero(projection.getPossibleCredit())
        );
    }

    public AttendanceSummary toSummary(
            LowAttendanceStudentProjection projection
    ) {
        if (projection == null) {
            return AttendanceSummary.empty();
        }

        return AttendanceSummary.of(
                valueOrZero(projection.getPresentCount()),
                valueOrZero(projection.getAbsentCount()),
                valueOrZero(projection.getLateCount()),
                valueOrZero(projection.getHalfDayCount()),
                valueOrZero(projection.getExcusedCount()),
                valueOrZero(projection.getEarnedCredit()),
                valueOrZero(projection.getPossibleCredit())
        );
    }

    public DailyStudentAttendanceRowResponse toDailyStudent(
            DailyStudentAttendanceProjection projection
    ) {
        requireProjection(
                projection,
                "Daily student attendance projection is required"
        );

        return new DailyStudentAttendanceRowResponse(
                projection.getAttendanceRecordId(),
                projection.getStudentEnrollmentId(),
                projection.getStudentUserId(),
                projection.getRollNumber(),
                attendanceStatus(projection.getRecordedStatus()),
                attendanceStatus(projection.getEffectiveStatus()),
                valueOrZero(projection.getEarnedCredit()),
                valueOrZero(projection.getPossibleCredit()),
                Boolean.TRUE.equals(
                        projection.getLatePenaltyApplied()
                ),
                projection.getRemarks()
        );
    }

    public StudentAttendanceHistoryRowResponse toHistoryRow(
            StudentAttendanceHistoryProjection projection
    ) {
        requireProjection(
                projection,
                "Student attendance history projection is required"
        );

        return new StudentAttendanceHistoryRowResponse(
                projection.getAttendanceSessionId(),
                projection.getAttendanceRecordId(),
                projection.getStudentEnrollmentId(),
                projection.getAcademicYearId(),
                projection.getGradeLevelId(),
                projection.getSectionId(),
                projection.getAttendanceDate(),
                attendanceStatus(projection.getRecordedStatus()),
                attendanceStatus(projection.getEffectiveStatus()),
                valueOrZero(projection.getEarnedCredit()),
                valueOrZero(projection.getPossibleCredit()),
                Boolean.TRUE.equals(
                        projection.getLatePenaltyApplied()
                ),
                normalizeOptionalText(projection.getRemarks()),
                submissionType(projection.getSubmissionType()),
                toOffsetDateTime(projection.getSubmittedAt()),
                toOffsetDateTime(projection.getLastUpdatedAt())
        );
    }

    public GradeAttendanceSectionRowResponse toGradeSection(
            GradeSectionAttendanceAggregateProjection projection
    ) {
        requireProjection(
                projection,
                "Grade section attendance projection is required"
        );

        return new GradeAttendanceSectionRowResponse(
                projection.getSectionId(),
                projection.getSectionCode(),
                projection.getSectionName(),
                valueOrZero(
                        projection.getSubmittedSessionCount()
                ),
                valueOrZero(
                        projection.getDistinctStudentCount()
                ),
                toSummary(projection)
        );
    }

    public LowAttendanceStudentResponse toLowAttendanceStudent(
            LowAttendanceStudentProjection projection
    ) {
        requireProjection(
                projection,
                "Low-attendance student projection is required"
        );

        return new LowAttendanceStudentResponse(
                projection.getStudentEnrollmentId(),
                projection.getStudentUserId(),
                projection.getGradeLevelId(),
                projection.getSectionId(),
                projection.getRollNumber(),
                toSummary(projection)
        );
    }

    public MissingAttendanceSessionResponse toMissingSession(
            MissingAttendanceSessionProjection projection
    ) {
        requireProjection(
                projection,
                "Missing attendance session projection is required"
        );

        return new MissingAttendanceSessionResponse(
                projection.getAcademicCalendarDayId(),
                projection.getAttendanceDate(),
                projection.getGradeLevelId(),
                projection.getSectionId(),
                projection.getSectionCode(),
                projection.getSectionName(),
                projection.getAttendanceSessionId(),
                projection.getAttendanceSessionId() == null
                        ? null
                        : sessionStatus(
                        projection.getLifecycleStatus()
                ),
                valueOrZero(
                        projection.getEligibleStudentCount()
                ),
                valueOrZero(
                        projection.getRecordedStudentCount()
                ),
                false,
                false
        );
    }

    private AttendanceStatus attendanceStatus(String value) {
        return requiredEnum(
                AttendanceStatus.class,
                value,
                "attendance status"
        );
    }

    private StudentAttendanceSubmissionType submissionType(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return requiredEnum(
                StudentAttendanceSubmissionType.class,
                value,
                "attendance submission type"
        );
    }

    private StudentAttendanceSessionStatus sessionStatus(
            String value
    ) {
        return requiredEnum(
                StudentAttendanceSessionStatus.class,
                value,
                "attendance session status"
        );
    }

    private <T extends Enum<T>> T requiredEnum(
            Class<T> enumType,
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Database returned an empty " + fieldName
            );
        }

        try {
            return Enum.valueOf(
                    enumType,
                    value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Database returned an unsupported "
                            + fieldName,
                    exception
            );
        }
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void requireProjection(
            Object projection,
            String message
    ) {
        if (projection == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private OffsetDateTime toOffsetDateTime(
            Instant value
    ) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC);
    }
}
