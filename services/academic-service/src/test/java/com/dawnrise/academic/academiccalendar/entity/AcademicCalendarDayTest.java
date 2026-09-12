package com.dawnrise.academic.academiccalendar.entity;

import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.enums.CalendarDayType;
import com.dawnrise.academic.academiccalendar.exception.InvalidAcademicCalendarException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicCalendarDayTest {

    @Test
    void acceptsHalfWeightSpecialWorkingDay() {
        AcademicCalendarDay day = day(
                CalendarDayType.SPECIAL_WORKING_DAY,
                AttendanceRequirement.REQUIRED,
                true,
                "0.50"
        );

        assertThat(day.getDayWeight()).isEqualByComparingTo("0.50");
    }

    @Test
    void acceptsEveryValidExamDayCombination() {
        assertThat(day(
                CalendarDayType.EXAM_DAY,
                AttendanceRequirement.REQUIRED,
                true,
                "1.00"
        ).isCountsTowardPercentage()).isTrue();

        assertThat(day(
                CalendarDayType.EXAM_DAY,
                AttendanceRequirement.REQUIRED,
                false,
                "0.00"
        ).isCountsTowardPercentage()).isFalse();

        assertThat(day(
                CalendarDayType.EXAM_DAY,
                AttendanceRequirement.OPTIONAL,
                false,
                "0.00"
        ).getAttendanceRequirement()).isEqualTo(AttendanceRequirement.OPTIONAL);

        assertThat(day(
                CalendarDayType.EXAM_DAY,
                AttendanceRequirement.NOT_APPLICABLE,
                false,
                "0.00"
        ).getAttendanceRequirement()).isEqualTo(
                AttendanceRequirement.NOT_APPLICABLE
        );
    }

    @Test
    void rejectsInvalidExamDayAttendanceCombinations() {
        assertInvalidExamDay(AttendanceRequirement.OPTIONAL, true, "0.00");
        assertInvalidExamDay(AttendanceRequirement.OPTIONAL, false, "0.50");
        assertInvalidExamDay(
                AttendanceRequirement.NOT_APPLICABLE,
                true,
                "0.00"
        );
        assertInvalidExamDay(AttendanceRequirement.REQUIRED, true, "0.00");
        assertInvalidExamDay(AttendanceRequirement.REQUIRED, false, "0.50");
    }

    @Test
    void acceptsSpecialFunctionRequirementVariants() {
        day(CalendarDayType.SPECIAL_FUNCTION,
                AttendanceRequirement.REQUIRED, false, "0.00");
        day(CalendarDayType.SPECIAL_FUNCTION,
                AttendanceRequirement.OPTIONAL, false, "0.00");
        day(CalendarDayType.SPECIAL_FUNCTION,
                AttendanceRequirement.NOT_APPLICABLE, false, "0.00");
    }

    @Test
    void rejectsHolidayThatRequiresAttendance() {
        assertThatThrownBy(() -> day(
                CalendarDayType.HOLIDAY,
                AttendanceRequirement.REQUIRED,
                false,
                "0.00"
        )).isInstanceOf(InvalidAcademicCalendarException.class);
    }

    @Test
    void rejectsBlankNameAndNote() {
        assertThatThrownBy(() -> new AcademicCalendarDay(
                10L,
                20L,
                LocalDate.of(2026, 6, 1),
                CalendarDayType.HOLIDAY,
                " ",
                AttendanceRequirement.NOT_APPLICABLE,
                false,
                BigDecimal.ZERO,
                null,
                30L
        )).isInstanceOf(InvalidAcademicCalendarException.class);
    }

    private static AcademicCalendarDay day(
            CalendarDayType type,
            AttendanceRequirement requirement,
            boolean counts,
            String weight
    ) {
        return new AcademicCalendarDay(
                10L,
                20L,
                LocalDate.of(2026, 6, 1),
                type,
                null,
                requirement,
                counts,
                new BigDecimal(weight),
                null,
                30L
        );
    }

    private static void assertInvalidExamDay(
            AttendanceRequirement requirement,
            boolean counts,
            String weight
    ) {
        assertThatThrownBy(() -> day(
                CalendarDayType.EXAM_DAY,
                requirement,
                counts,
                weight
        )).isInstanceOf(InvalidAcademicCalendarException.class);
    }
}
