package com.dawnrise.academic.academiccalendar.service.impl;

import com.dawnrise.academic.academiccalendar.dto.*;
import com.dawnrise.academic.academiccalendar.entity.AcademicCalendarDay;
import com.dawnrise.academic.academiccalendar.enums.*;
import com.dawnrise.academic.academiccalendar.exception.*;
import com.dawnrise.academic.academiccalendar.repository.AcademicCalendarDayRepository;
import com.dawnrise.academic.academiccalendar.service.AcademicCalendarService;
import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.studentattendance.config.StudentAttendanceProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class AcademicCalendarServiceImpl
        implements AcademicCalendarService {

    private final AcademicYearRepository academicYearRepository;
    private final AcademicCalendarDayRepository calendarDayRepository;
    private final StudentAttendanceProperties properties;

    public AcademicCalendarServiceImpl(
            AcademicYearRepository academicYearRepository,
            AcademicCalendarDayRepository calendarDayRepository,
            StudentAttendanceProperties properties
    ) {
        this.academicYearRepository = academicYearRepository;
        this.calendarDayRepository = calendarDayRepository;
        this.properties = properties;
    }

    @Override
    public List<AcademicCalendarDayResponse> initialize(
            long organizationId,
            long academicYearId,
            long actorUserId,
            InitializeAcademicCalendarRequest request
    ) {
        AcademicYear year = findYearForUpdate(organizationId, academicYearId);
        ensureModifiable(year);
        long dayCount = ChronoUnit.DAYS.between(
                year.getStartDate(),
                year.getEndDate()
        ) + 1;
        if (dayCount > properties.getMaxCalendarDaysPerAcademicYear()) {
            throw new InvalidAcademicCalendarException(
                    "Academic year exceeds configured calendar day limit"
            );
        }
        if (calendarDayRepository.existsByOrganizationIdAndAcademicYearId(
                organizationId,
                academicYearId
        )) {
            throw new AcademicCalendarConflictException(
                    "Academic calendar is already initialized"
            );
        }
        BigDecimal weight = request.defaultWorkingDayWeight() == null
                ? properties.getDefaultWorkingDayWeight()
                : request.defaultWorkingDayWeight();
        List<AcademicCalendarDay> days = new ArrayList<>((int) dayCount);
        for (LocalDate date = year.getStartDate();
                !date.isAfter(year.getEndDate());
                date = date.plusDays(1)) {
            boolean working = request.workingWeekDays()
                    .contains(date.getDayOfWeek());
            days.add(new AcademicCalendarDay(
                    organizationId,
                    academicYearId,
                    date,
                    working ? CalendarDayType.WORKING_DAY
                            : CalendarDayType.WEEKLY_OFF,
                    null,
                    working ? AttendanceRequirement.REQUIRED
                            : AttendanceRequirement.NOT_APPLICABLE,
                    working,
                    working ? weight : BigDecimal.ZERO,
                    null,
                    actorUserId
            ));
        }
        try {
            return calendarDayRepository.saveAllAndFlush(days)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        } catch (DataIntegrityViolationException exception) {
            throw new AcademicCalendarConflictException(
                    "Academic calendar has already been initialized for this academic year",
                    exception
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicCalendarDayResponse> getDays(
            long organizationId,
            long academicYearId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        AcademicYear year = findYear(organizationId, academicYearId);
        LocalDate start = startDate == null ? year.getStartDate() : startDate;
        LocalDate end = endDate == null ? year.getEndDate() : endDate;
        validateDateInYear(year, start);
        validateDateInYear(year, end);
        if (end.isBefore(start)) {
            throw new InvalidAcademicCalendarException(
                    "End date cannot be before start date"
            );
        }
        long requestedDayCount = ChronoUnit.DAYS.between(start, end) + 1;
        if (requestedDayCount > properties.getMaxRecordsPerRequest()) {
            throw new InvalidAcademicCalendarException(
                    "Calendar date range exceeds configured request limit"
            );
        }
        return calendarDayRepository
                .findAllByOrganizationIdAndAcademicYearIdAndCalendarDateBetweenOrderByCalendarDateAscIdAsc(
                        organizationId,
                        academicYearId,
                        start,
                        end
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicCalendarDayResponse getDay(
            long organizationId,
            long academicYearId,
            long calendarDayId
    ) {
        findYear(organizationId, academicYearId);
        return toResponse(findDay(organizationId, academicYearId, calendarDayId));
    }

    @Override
    public AcademicCalendarDayResponse updateDay(
            long organizationId,
            long academicYearId,
            long calendarDayId,
            long actorUserId,
            UpdateAcademicCalendarDayRequest request
    ) {
        AcademicYear year = findYear(organizationId, academicYearId);
        ensureModifiable(year);
        AcademicCalendarDay day = findDay(
                organizationId,
                academicYearId,
                calendarDayId
        );
        validateDateInYear(year, day.getCalendarDate());
        if (!Objects.equals(day.getVersion(), request.expectedVersion())) {
            throw new AcademicCalendarConflictException(
                    "Academic calendar day was modified by another request"
            );
        }
        if (request.dayType() == CalendarDayType.WORKING_DAY
                || request.dayType() == CalendarDayType.WEEKLY_OFF) {
            throw new InvalidAcademicCalendarException(
                    "Calendar overrides must use an exam, holiday, vacation, special working day, or special function day type"
            );
        }
        day.update(
                request.dayType(),
                request.name(),
                request.attendanceRequirement(),
                request.countsTowardPercentage(),
                request.dayWeight(),
                request.note(),
                actorUserId
        );
        return toResponse(calendarDayRepository.saveAndFlush(day));
    }

    private AcademicYear findYear(long organizationId, long academicYearId) {
        return academicYearRepository
                .findByIdAndOrganizationId(academicYearId, organizationId)
                .orElseThrow(() ->
                        new AcademicYearNotFoundException(
                                "Academic year not found"
                        )
                );
    }

    private AcademicYear findYearForUpdate(
            long organizationId,
            long academicYearId
    ) {
        return academicYearRepository
                .findByIdAndOrganizationIdForUpdate(
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new AcademicYearNotFoundException(
                                "Academic year not found"
                        )
                );
    }

    private AcademicCalendarDay findDay(
            long organizationId,
            long academicYearId,
            long calendarDayId
    ) {
        return calendarDayRepository
                .findByIdAndOrganizationIdAndAcademicYearId(
                        calendarDayId,
                        organizationId,
                        academicYearId
                )
                .orElseThrow(() ->
                        new AcademicCalendarDayNotFoundException(
                                "Academic calendar day not found"
                        )
                );
    }

    private void ensureModifiable(AcademicYear year) {
        if (year.getStatus() == AcademicYearStatus.CLOSED
                || year.getStatus() == AcademicYearStatus.VOIDED) {
            throw new AcademicCalendarConflictException(
                    "Closed or voided academic years cannot be modified"
            );
        }
    }

    private void validateDateInYear(AcademicYear year, LocalDate date) {
        if (date == null
                || date.isBefore(year.getStartDate())
                || date.isAfter(year.getEndDate())) {
            throw new InvalidAcademicCalendarException(
                    "Calendar date must be within the academic year"
            );
        }
    }

    private AcademicCalendarDayResponse toResponse(AcademicCalendarDay day) {
        return new AcademicCalendarDayResponse(
                day.getId(),
                day.getAcademicYearId(),
                day.getCalendarDate(),
                day.getDayType(),
                day.getName(),
                day.getAttendanceRequirement(),
                day.isCountsTowardPercentage(),
                day.getDayWeight(),
                day.getNote(),
                day.getVersion(),
                day.getCreatedAt(),
                day.getUpdatedAt()
        );
    }
}
