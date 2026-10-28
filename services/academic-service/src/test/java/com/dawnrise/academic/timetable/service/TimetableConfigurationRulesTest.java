package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.*;
import com.dawnrise.academic.timetable.exception.InvalidTimetableConfigurationException;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimetableConfigurationRulesTest {
    @Test
    void gradeOverrideWinsAndWingDefaultIsFallback() {
        assertEquals(22L, TimetableConfigurationRules.resolveScheduleId(22L, 11L));
        assertEquals(11L, TimetableConfigurationRules.resolveScheduleId(null, 11L));
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.resolveScheduleId(null, null));
    }

    @Test
    void rejectsActualIntervalOverlapButAllowsTouchingIntervals() {
        BellIntervalRequest first = teaching("Period 1", "09:00", "09:40", 1);
        BellIntervalRequest touching = teaching("Period 2", "09:40", "10:20", 2);
        BellDayRequest valid = day(first, touching);
        assertDoesNotThrow(() -> TimetableConfigurationRules.validateDays(List.of(valid)));

        BellIntervalRequest overlap = teaching("Period 2", "09:35", "10:15", 2);
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateDays(List.of(day(first, overlap))));
    }

    @Test
    void rejectsSubMinuteBellAndAvailabilityTimes() {
        BellIntervalRequest subMinute = new BellIntervalRequest(IntervalType.TEACHING, "Period 1",
                LocalTime.of(9, 0, 30), time("09:40"), 1, CoverageMode.SECTION,
                AssigneeRequirement.QUALIFIED_SUBJECT_TEACHER, true);
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateDays(List.of(day(subMinute))));
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateAvailabilityTimes(
                        LocalTime.of(9, 0, 0, 1), time("10:00")));
    }

    @Test
    void distinguishesCoverageSemanticsForAssemblyAndRecess() {
        BellIntervalRequest assembly = new BellIntervalRequest(IntervalType.ASSEMBLY, "Assembly",
                time("09:00"), time("09:20"), 1, CoverageMode.WHOLE_WING,
                AssigneeRequirement.TEACHER_OR_APPROVED_SUPERVISOR, true);
        BellIntervalRequest recess = new BellIntervalRequest(IntervalType.RECESS, "Recess",
                time("09:20"), time("09:40"), 2, CoverageMode.NONE, AssigneeRequirement.NONE, false);
        assertDoesNotThrow(() -> TimetableConfigurationRules.validateDays(List.of(day(assembly, recess))));

        BellIntervalRequest invalidRecess = new BellIntervalRequest(IntervalType.RECESS, "Recess",
                time("09:20"), time("09:40"), 2, CoverageMode.SECTION,
                AssigneeRequirement.TEACHER_OR_APPROVED_SUPERVISOR, true);
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateDays(List.of(day(assembly, invalidRecess))));
    }

    @Test
    void rejectsImpossibleDailyFrequencyAndDoubleBlocks() {
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateRequirement(4, true, 1, 0, 5, 30, 5));
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateRequirement(6, false, 2, 3, 5, 30, 2));
        assertDoesNotThrow(() -> TimetableConfigurationRules.validateRequirement(6, true, 2, 2, 5, 30, 3));
    }

    @Test
    void validatesTeacherTenantEligibilityAndAvailabilityRange() {
        TeachingEligibilityResponse eligible = new TeachingEligibilityResponse(41L, 7L, "Teacher", true, null);
        assertDoesNotThrow(() -> TimetableConfigurationRules.validateTeacherEligibility(7L, 41L, eligible));
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateTeacherEligibility(8L, 41L, eligible));
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateTeacherEligibility(7L, 42L, eligible));
        assertDoesNotThrow(() -> TimetableConfigurationRules.validateAvailabilityTimes(time("09:00"), time("12:00")));
        assertThrows(InvalidTimetableConfigurationException.class,
                () -> TimetableConfigurationRules.validateAvailabilityTimes(time("12:00"), time("09:00")));
    }

    private BellDayRequest day(BellIntervalRequest... intervals) {
        return new BellDayRequest(1, true, time("09:00"), time("16:00"), List.of(intervals));
    }
    private BellIntervalRequest teaching(String name, String start, String end, int order) {
        return new BellIntervalRequest(IntervalType.TEACHING, name, time(start), time(end), order,
                CoverageMode.SECTION, AssigneeRequirement.QUALIFIED_SUBJECT_TEACHER, true);
    }
    private LocalTime time(String value) { return LocalTime.parse(value); }
}
