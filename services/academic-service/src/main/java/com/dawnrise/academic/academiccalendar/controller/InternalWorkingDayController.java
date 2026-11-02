package com.dawnrise.academic.academiccalendar.controller;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/internal/v1/organizations/{organizationId}/working-days")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalWorkingDayController {
    private final JdbcClient jdbc;
    public InternalWorkingDayController(JdbcClient jdbc){this.jdbc=jdbc;}

    @GetMapping
    public Response workingDays(@PathVariable long organizationId,@RequestParam LocalDate start,
                                @RequestParam LocalDate end) {
        if(end.isBefore(start)||start.plusYears(1).isBefore(end)) throw new IllegalArgumentException("Invalid date range");
        List<LocalDate> configured=jdbc.sql("""
                SELECT calendar_date FROM academic_calendar_days
                WHERE organization_id=:organizationId AND calendar_date BETWEEN :start AND :end
                  AND attendance_requirement='REQUIRED' ORDER BY calendar_date
                """).param("organizationId",organizationId).param("start",start).param("end",end)
                .query(LocalDate.class).list();
        List<LocalDate> excluded=jdbc.sql("""
                SELECT calendar_date FROM academic_calendar_days
                WHERE organization_id=:organizationId AND calendar_date BETWEEN :start AND :end
                  AND attendance_requirement<>'REQUIRED' ORDER BY calendar_date
                """).param("organizationId",organizationId).param("start",start).param("end",end)
                .query(LocalDate.class).list();
        var excludedSet=Set.copyOf(excluded); var configuredSet=Set.copyOf(configured);
        List<LocalDate> working=start.datesUntil(end.plusDays(1)).filter(date->configuredSet.contains(date)
                || (!excludedSet.contains(date)&&date.getDayOfWeek()!=DayOfWeek.SATURDAY&&date.getDayOfWeek()!=DayOfWeek.SUNDAY)).toList();
        return new Response(working,excluded);
    }
    public record Response(List<LocalDate> workingDates,List<LocalDate> excludedDates){}
}
