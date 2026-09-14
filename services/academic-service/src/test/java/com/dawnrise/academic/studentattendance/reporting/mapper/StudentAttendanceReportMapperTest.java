package com.dawnrise.academic.studentattendance.reporting.mapper;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;
import com.dawnrise.academic.studentattendance.reporting.dto.AttendanceSummary;
import com.dawnrise.academic.studentattendance.reporting.dto.MissingAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.reporting.projection.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.Map.entry;

class StudentAttendanceReportMapperTest {

    private final StudentAttendanceReportMapper mapper =
            new StudentAttendanceReportMapper();

    @Test
    void nullAggregateValuesBecomeZeroSummary() {
        AttendanceSummary summary =
                mapper.toSummary(projection(
                        AttendanceAggregateProjection.class,
                        Map.of()
                ));

        assertThat(summary.statusCounts().totalCount()).isZero();
        assertThat(summary.credits().earnedCredit())
                .isEqualByComparingTo("0.00");
        assertThat(summary.credits().possibleCredit())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void convertsDailyStatusesAndNormalizesCredits() {
        var row = mapper.toDailyStudent(projection(
                DailyStudentAttendanceProjection.class,
                Map.of(
                        "attendanceRecordId", 1L,
                        "studentEnrollmentId", 2L,
                        "studentUserId", 3L,
                        "rollNumber", "  A-1 ",
                        "recordedStatus", "late",
                        "effectiveStatus", "HALF_DAY",
                        "earnedCredit", new BigDecimal("0.50"),
                        "possibleCredit", BigDecimal.ONE,
                        "latePenaltyApplied", Boolean.TRUE,
                        "remarks", "  penalty "
                )
        ));

        assertThat(row.recordedStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(row.effectiveStatus())
                .isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(row.rollNumber()).isEqualTo("A-1");
        assertThat(row.remarks()).isEqualTo("penalty");
        assertThat(row.latePenaltyApplied()).isTrue();
    }

    @Test
    void convertsHistorySubmissionTypeAndTrimsBlankRemarksToNull() {
        Instant submittedAt = Instant.parse("2026-09-11T04:30:00Z");
        var row = mapper.toHistoryRow(projection(
                StudentAttendanceHistoryProjection.class,
                Map.ofEntries(
                        entry("attendanceSessionId", 10L),
                        entry("attendanceRecordId", 11L),
                        entry("studentEnrollmentId", 12L),
                        entry("academicYearId", 13L),
                        entry("gradeLevelId", 14L),
                        entry("sectionId", 15L),
                        entry("attendanceDate", LocalDate.of(2026, 9, 11)),
                        entry("recordedStatus", "PRESENT"),
                        entry("effectiveStatus", "PRESENT"),
                        entry("earnedCredit", BigDecimal.ONE),
                        entry("possibleCredit", BigDecimal.ONE),
                        entry("latePenaltyApplied", Boolean.FALSE),
                        entry("remarks", "   "),
                        entry("submissionType", "automatic"),
                        entry("submittedAt", submittedAt),
                        entry("lastUpdatedAt", submittedAt)
                )
        ));

        assertThat(row.submissionType())
                .isEqualTo(StudentAttendanceSubmissionType.AUTOMATIC);
        assertThat(row.remarks()).isNull();
        assertThat(row.submittedAt())
                .isEqualTo(submittedAt.atOffset(ZoneOffset.UTC));
        assertThat(row.lastUpdatedAt())
                .isEqualTo(submittedAt.atOffset(ZoneOffset.UTC));
    }

    @Test
    void unsupportedAndBlankDatabaseEnumsFailClosed() {
        DailyStudentAttendanceProjection blankStatus = projection(
                DailyStudentAttendanceProjection.class,
                Map.of(
                        "attendanceRecordId", 1L,
                        "studentEnrollmentId", 2L,
                        "studentUserId", 3L,
                        "rollNumber", "A-1",
                        "recordedStatus", " ",
                        "effectiveStatus", "PRESENT",
                        "earnedCredit", BigDecimal.ONE,
                        "possibleCredit", BigDecimal.ONE
                )
        );
        StudentAttendanceHistoryProjection unsupportedSubmission =
                projection(
                        StudentAttendanceHistoryProjection.class,
                        Map.ofEntries(
                                entry("attendanceSessionId", 10L),
                                entry("attendanceRecordId", 11L),
                                entry("studentEnrollmentId", 12L),
                                entry("academicYearId", 13L),
                                entry("gradeLevelId", 14L),
                                entry("sectionId", 15L),
                                entry("attendanceDate",
                                        LocalDate.of(2026, 9, 11)),
                                entry("recordedStatus", "PRESENT"),
                                entry("effectiveStatus", "PRESENT"),
                                entry("earnedCredit", BigDecimal.ONE),
                                entry("possibleCredit", BigDecimal.ONE),
                                entry("submissionType", "BROKEN")
                        )
                );

        assertThatThrownBy(() -> mapper.toDailyStudent(blankStatus))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty attendance status");
        assertThatThrownBy(() ->
                mapper.toHistoryRow(unsupportedSubmission)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported attendance submission type");
    }

    @Test
    void mapsGradeSectionLowAttendanceAndMissingSessionRows() {
        var section = mapper.toGradeSection(projection(
                GradeSectionAttendanceAggregateProjection.class,
                Map.of(
                        "sectionId", 31L,
                        "sectionCode", " A ",
                        "sectionName", " Alpha ",
                        "presentCount", 1L,
                        "absentCount", 1L,
                        "earnedCredit", BigDecimal.ONE,
                        "possibleCredit", new BigDecimal("2.00"),
                        "submittedSessionCount", 2L,
                        "distinctStudentCount", 3L
                )
        ));
        var low = mapper.toLowAttendanceStudent(projection(
                LowAttendanceStudentProjection.class,
                Map.of(
                        "studentEnrollmentId", 41L,
                        "studentUserId", 42L,
                        "gradeLevelId", 43L,
                        "sectionId", 44L,
                        "rollNumber", " 7 ",
                        "absentCount", 2L,
                        "earnedCredit", BigDecimal.ZERO,
                        "possibleCredit", new BigDecimal("2.00")
                )
        ));
        MissingAttendanceSessionResponse missing =
                mapper.toMissingSession(projection(
                        MissingAttendanceSessionProjection.class,
                        Map.of(
                                "academicCalendarDayId", 51L,
                                "attendanceDate",
                                LocalDate.of(2026, 9, 12),
                                "gradeLevelId", 52L,
                                "sectionId", 53L,
                                "sectionCode", " B ",
                                "sectionName", " Beta ",
                                "attendanceSessionId", 54L,
                                "lifecycleStatus", "draft",
                                "eligibleStudentCount", 30L,
                                "recordedStudentCount", 29L
                        )
                ));

        assertThat(section.sectionCode()).isEqualTo("A");
        assertThat(section.summary().statusCounts().totalCount())
                .isEqualTo(2);
        assertThat(low.rollNumber()).isEqualTo("7");
        assertThat(low.summary().credits().attendancePercentage())
                .isEqualByComparingTo("0.00");
        assertThat(missing.lifecycleStatus())
                .isEqualTo(StudentAttendanceSessionStatus.DRAFT);
        assertThat(missing.rosterIncomplete()).isTrue();
    }

    @Test
    void missingSessionWithNullSessionDoesNotParseNullLifecycleStatus() {
        MissingAttendanceSessionResponse missing =
                mapper.toMissingSession(projection(
                        MissingAttendanceSessionProjection.class,
                        Map.of(
                                "academicCalendarDayId", 51L,
                                "attendanceDate",
                                LocalDate.of(2026, 9, 12),
                                "gradeLevelId", 52L,
                                "sectionId", 53L,
                                "sectionCode", " B ",
                                "sectionName", " Beta ",
                                "eligibleStudentCount", 30L
                        )
                ));

        assertThat(missing.attendanceSessionId()).isNull();
        assertThat(missing.lifecycleStatus()).isNull();
        assertThat(missing.recordedStudentCount()).isZero();
    }

    @SuppressWarnings("unchecked")
    private static <T> T projection(
            Class<T> projectionType,
            Map<String, Object> values
    ) {
        return (T) Proxy.newProxyInstance(
                projectionType.getClassLoader(),
                new Class<?>[]{projectionType},
                (proxy, method, args) -> {
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    if (method.getName().equals("toString")) {
                        return projectionType.getSimpleName() + values;
                    }
                    if (method.getName().startsWith("get")) {
                        String property =
                                Character.toLowerCase(
                                        method.getName().charAt(3)
                                ) + method.getName().substring(4);
                        return values.get(property);
                    }
                    throw new UnsupportedOperationException(
                            method.getName()
                    );
                }
        );
    }
}
