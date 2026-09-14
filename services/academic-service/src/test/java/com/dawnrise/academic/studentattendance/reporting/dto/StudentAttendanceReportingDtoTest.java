package com.dawnrise.academic.studentattendance.reporting.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceReportingDtoTest {

    @Test
    void creditPercentageIsCalculatedAndRounded() {
        AttendanceCreditSummary summary =
                AttendanceCreditSummary.from(
                        new BigDecimal("2.005"),
                        new BigDecimal("3.005")
                );

        assertThat(summary.earnedCredit()).isEqualByComparingTo("2.01");
        assertThat(summary.possibleCredit()).isEqualByComparingTo("3.01");
        assertThat(summary.attendancePercentage())
                .isEqualByComparingTo("66.78");
    }

    @Test
    void zeroPossibleCreditProducesZeroPercentage() {
        AttendanceCreditSummary summary =
                AttendanceCreditSummary.from(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                );

        assertThat(summary.attendancePercentage())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void invalidCreditsAreRejected() {
        assertThatThrownBy(() -> AttendanceCreditSummary.from(
                new BigDecimal("-0.01"),
                BigDecimal.ONE
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> AttendanceCreditSummary.from(
                BigDecimal.ONE,
                new BigDecimal("-0.01")
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> AttendanceCreditSummary.from(
                new BigDecimal("2.00"),
                BigDecimal.ONE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusTotalsAreCalculatedAndNegativeCountsRejected() {
        AttendanceStatusCountSummary summary =
                AttendanceStatusCountSummary.of(1, 2, 3, 4, 5);

        assertThat(summary.totalCount()).isEqualTo(15);

        assertThatThrownBy(() ->
                AttendanceStatusCountSummary.of(-1, 0, 0, 0, 0)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dateRangeIsInclusiveAndRejectsReversedRange() {
        AttendanceReportDateRange range =
                new AttendanceReportDateRange(
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 12)
                );

        assertThat(range.inclusiveDayCount()).isEqualTo(3);
        assertThat(range.contains(LocalDate.of(2026, 9, 10))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 9, 12))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 9, 13))).isFalse();

        assertThatThrownBy(() -> new AttendanceReportDateRange(
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 10)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void responseListsAreDefensiveCopies() {
        DailyStudentAttendanceRowResponse row =
                new DailyStudentAttendanceRowResponse(
                        1L,
                        2L,
                        3L,
                        "  A-1 ",
                        AttendanceStatus.PRESENT,
                        AttendanceStatus.PRESENT,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        false,
                        "  ok "
                );
        List<DailyStudentAttendanceRowResponse> students =
                new ArrayList<>(List.of(row));

        DailySectionAttendanceReportResponse response =
                new DailySectionAttendanceReportResponse(
                        10L,
                        7L,
                        8L,
                        9L,
                        11L,
                        12L,
                        LocalDate.of(2026, 9, 10),
                        StudentAttendanceSessionStatus.SUBMITTED,
                        null,
                        null,
                        AttendanceSummary.empty(),
                        students
                );
        students.clear();

        assertThat(row.rollNumber()).isEqualTo("A-1");
        assertThat(row.remarks()).isEqualTo("ok");
        assertThat(response.students()).hasSize(1);
        assertThatThrownBy(() -> response.students().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        MissingAttendanceReportResponse missing =
                new MissingAttendanceReportResponse(
                        7L,
                        8L,
                        9L,
                        11L,
                        new AttendanceReportDateRange(
                                LocalDate.of(2026, 9, 10),
                                LocalDate.of(2026, 9, 10)
                        ),
                        0,
                        20,
                        false,
                        null
                );

        assertThat(missing.items()).isEmpty();
    }

    @Test
    void missingSessionResponseRetainsScopeFieldsAndHandlesNullSession() {
        MissingAttendanceSessionResponse missing =
                new MissingAttendanceSessionResponse(
                        12L,
                        LocalDate.of(2026, 9, 10),
                        9L,
                        11L,
                        " A ",
                        " Alpha ",
                        null,
                        null,
                        30L,
                        0L,
                        false,
                        true
                );

        assertThat(missing.sectionCode()).isEqualTo("A");
        assertThat(missing.sectionName()).isEqualTo("Alpha");
        assertThat(missing.attendanceSessionId()).isNull();
        assertThat(missing.sessionMissing()).isTrue();
        assertThat(missing.rosterIncomplete()).isFalse();

        assertThatThrownBy(() -> new MissingAttendanceSessionResponse(
                12L,
                LocalDate.of(2026, 9, 10),
                9L,
                11L,
                "A",
                "Alpha",
                null,
                StudentAttendanceSessionStatus.DRAFT,
                30L,
                0L,
                false,
                false
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingSessionBooleansAreRecordComponentsAndServerDerived() {
        MissingAttendanceSessionResponse missing =
                new MissingAttendanceSessionResponse(
                        12L,
                        LocalDate.of(2026, 9, 10),
                        9L,
                        11L,
                        "A",
                        "Alpha",
                        null,
                        null,
                        30L,
                        0L,
                        false,
                        true
                );
        MissingAttendanceSessionResponse incomplete =
                new MissingAttendanceSessionResponse(
                        12L,
                        LocalDate.of(2026, 9, 10),
                        9L,
                        11L,
                        "A",
                        "Alpha",
                        50L,
                        StudentAttendanceSessionStatus.DRAFT,
                        30L,
                        29L,
                        true,
                        false
                );

        assertThat(recordComponentNames(
                MissingAttendanceSessionResponse.class
        )).contains("sessionMissing", "rosterIncomplete");
        assertThat(missing.sessionMissing()).isTrue();
        assertThat(missing.rosterIncomplete()).isFalse();
        assertThat(incomplete.sessionMissing()).isFalse();
        assertThat(incomplete.rosterIncomplete()).isTrue();
    }

    @Test
    void attendancePageResponseCopiesContentAndExposesStableMetadata() {
        List<String> content = new ArrayList<>(List.of("A", "B"));
        Page<String> page =
                new PageImpl<>(content, PageRequest.of(1, 2), 5);

        AttendancePageResponse<String> response =
                AttendancePageResponse.from(page);
        content.clear();

        assertThat(response.content()).containsExactly("A", "B");
        assertThatThrownBy(() -> response.content().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.numberOfElements()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
        assertThat(response.empty()).isFalse();
    }

    @Test
    void publicReportingResponseDtosDoNotExposeSpringPage() {
        assertThat(recordComponentTypes(
                StudentAttendanceHistoryReportResponse.class
        )).doesNotContain(Page.class);
        assertThat(recordComponentTypes(
                LowAttendanceReportResponse.class
        )).doesNotContain(Page.class);
    }

    @Test
    void reportWrappersRejectMissingRequiredStructure() {
        AttendanceReportDateRange range =
                new AttendanceReportDateRange(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30)
                );

        assertThatThrownBy(() -> new DailySectionAttendanceReportResponse(
                1L, 7L, 8L, 9L, 10L, 20L, null,
                StudentAttendanceSessionStatus.SUBMITTED, null, null,
                AttendanceSummary.empty(), List.of()
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectionAttendanceSummaryResponse(
                7L, 8L, 9L, 10L, null, 0, 0,
                AttendanceSummary.empty()
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GradeAttendanceSummaryResponse(
                7L, 8L, 9L, range, null, List.of()
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MonthlyStudentAttendanceReportResponse(
                7L, 8L, 9L, 10L, 99L, YearMonth.of(2026, 9),
                0, null
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StudentAttendanceHistoryReportResponse(
                7L, 8L, 9L, 10L, 99L, range,
                AttendanceSummary.empty(), null
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LowAttendanceReportResponse(
                7L, 8L, 9L, 10L, range, BigDecimal.TEN, null
        )).isInstanceOf(NullPointerException.class);

        assertThat(new LowAttendanceReportResponse(
                7L,
                8L,
                9L,
                10L,
                range,
                BigDecimal.TEN,
                AttendancePageResponse.from(Page.empty())
        ).students().empty()).isTrue();
    }

    private static List<String> recordComponentNames(
            Class<?> recordType
    ) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static List<Class<?>> recordComponentTypes(
            Class<?> recordType
    ) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();
    }
}
