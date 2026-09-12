package com.dawnrise.academic.academiccalendar.service;

import com.dawnrise.academic.academiccalendar.dto.*;

import java.time.LocalDate;
import java.util.List;

public interface AcademicCalendarService {
    List<AcademicCalendarDayResponse> initialize(
            long organizationId,
            long academicYearId,
            long actorUserId,
            InitializeAcademicCalendarRequest request
    );
    List<AcademicCalendarDayResponse> getDays(
            long organizationId,
            long academicYearId,
            LocalDate startDate,
            LocalDate endDate
    );
    AcademicCalendarDayResponse getDay(
            long organizationId,
            long academicYearId,
            long calendarDayId
    );
    AcademicCalendarDayResponse updateDay(
            long organizationId,
            long academicYearId,
            long calendarDayId,
            long actorUserId,
            UpdateAcademicCalendarDayRequest request
    );
}
