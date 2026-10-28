package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.*;
import com.dawnrise.academic.timetable.exception.InvalidTimetableConfigurationException;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class TimetableConfigurationRules {
    private TimetableConfigurationRules() {}

    static Long resolveScheduleId(Long gradeOverrideId, Long wingDefaultId) {
        if (gradeOverrideId != null) return gradeOverrideId;
        if (wingDefaultId != null) return wingDefaultId;
        throw new InvalidTimetableConfigurationException("No grade override or wing default bell schedule is configured");
    }

    static void validateDays(List<BellDayRequest> days) {
        Set<Integer> seenDays = new LinkedHashSet<>();
        for (BellDayRequest day : days) {
            if (!seenDays.add(day.dayOfWeek())) fail("A bell schedule may define each day only once");
            List<BellIntervalRequest> intervals = day.intervals() == null ? List.of() : day.intervals();
            if (!day.workingDay() && (day.dayStartTime() != null || day.dayEndTime() != null || !intervals.isEmpty()))
                fail("Non-working days cannot contain times or intervals");
            if (day.workingDay() && (day.dayStartTime() == null || day.dayEndTime() == null || !day.dayEndTime().isAfter(day.dayStartTime())))
                fail("Working days require valid start and end times");
            requireWholeMinute(day.dayStartTime(), "Working-day start time");
            requireWholeMinute(day.dayEndTime(), "Working-day end time");
            List<BellIntervalRequest> sorted = intervals.stream().sorted(Comparator.comparing(BellIntervalRequest::startTime)).toList();
            Set<Integer> orders = new LinkedHashSet<>();
            LocalTime previousEnd = null;
            for (BellIntervalRequest interval : sorted) {
                if (!orders.add(interval.displayOrder())) fail("Interval display orders must be unique within a day");
                if (!interval.endTime().isAfter(interval.startTime())) fail("Interval end time must be after start time");
                requireWholeMinute(interval.startTime(), "Interval start time");
                requireWholeMinute(interval.endTime(), "Interval end time");
                if (day.workingDay() && (interval.startTime().isBefore(day.dayStartTime()) || interval.endTime().isAfter(day.dayEndTime())))
                    fail("Intervals must be within the working day's start and end times");
                if (previousEnd != null && interval.startTime().isBefore(previousEnd)) fail("Bell schedule intervals cannot overlap");
                validateSemantics(interval);
                previousEnd = interval.endTime();
            }
        }
    }

    static void validateRequirement(int periodsPerWeek, boolean requiredDaily, int maximumPerDay,
                                    int doubleBlocks, int workingDays, int teachingCapacity,
                                    long daysWithAdjacentTeachingPair) {
        if (periodsPerWeek > teachingCapacity) fail("Weekly periods exceed resolved teaching-slot capacity");
        if (requiredDaily && periodsPerWeek < workingDays) fail("Daily requirement needs at least one period per working day");
        if ((long) maximumPerDay * workingDays < periodsPerWeek)
            fail("Maximum periods per day cannot accommodate the weekly requirement");
        if (doubleBlocks * 2 > periodsPerWeek) fail("Double-period blocks use more periods than the weekly requirement");
        if (doubleBlocks > daysWithAdjacentTeachingPair)
            fail("Not enough working days contain adjacent teaching slots for requested double periods");
    }

    static void validateTeacherEligibility(long organizationId, long teacherUserId,
                                           TeachingEligibilityResponse response) {
        if (response == null || !response.eligible() || response.organizationId() == null ||
                response.organizationId() != organizationId || response.userId() == null ||
                response.userId() != teacherUserId) {
            fail("Teacher is not eligible in this organization");
        }
    }

    static void validateAvailabilityTimes(LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start))
            fail("Teacher availability end time must be after start time");
        requireWholeMinute(start, "Teacher availability start time");
        requireWholeMinute(end, "Teacher availability end time");
    }

    private static void requireWholeMinute(LocalTime value, String label) {
        if (value != null && (value.getSecond() != 0 || value.getNano() != 0))
            fail(label + " must use whole-minute precision");
    }

    private static void validateSemantics(BellIntervalRequest i) {
        boolean valid = switch (i.intervalType()) {
            case TEACHING -> i.coverageMode() == CoverageMode.SECTION && i.assigneeRequirement() == AssigneeRequirement.QUALIFIED_SUBJECT_TEACHER && i.schedulable();
            case ASSEMBLY -> (i.coverageMode() == CoverageMode.SECTION_GROUP || i.coverageMode() == CoverageMode.WHOLE_WING) && i.assigneeRequirement() == AssigneeRequirement.TEACHER_OR_APPROVED_SUPERVISOR && i.schedulable();
            case SUPERVISED_ACTIVITY -> (i.coverageMode() == CoverageMode.SECTION || i.coverageMode() == CoverageMode.SECTION_GROUP) && i.assigneeRequirement() == AssigneeRequirement.TEACHER_OR_APPROVED_SUPERVISOR && i.schedulable();
            case RECESS, TRANSITION -> i.coverageMode() == CoverageMode.NONE && i.assigneeRequirement() == AssigneeRequirement.NONE && !i.schedulable();
            case FIXED_NON_STUDENT -> i.coverageMode() == CoverageMode.NONE;
        };
        if (!valid) fail("Interval coverage and assignee settings do not match " + i.intervalType());
    }

    private static void fail(String message) { throw new InvalidTimetableConfigurationException(message); }
}
