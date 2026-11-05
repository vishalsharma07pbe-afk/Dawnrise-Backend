package com.dawnrise.academic.studentattendance.reporting.dto;

import java.time.LocalDate;

public record GuardianAttendanceSummaryResponse(
        long studentUserId,
        long recordedDays,
        Double percentage,
        long present,
        long absent,
        long late,
        long halfDay,
        long excused,
        LocalDate latestDate,
        String latestStatus
) {}
