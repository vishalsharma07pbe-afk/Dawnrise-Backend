package com.dawnrise.academic.academiccalendar.controller;

import com.dawnrise.academic.academiccalendar.dto.*;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.academiccalendar.service.AcademicCalendarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/academic-years/{academicYearId}/calendar-days")
@Validated
public class AcademicCalendarController {

    private final AcademicCalendarService service;

    public AcademicCalendarController(AcademicCalendarService service) {
        this.service = service;
    }

    @PostMapping("/initialize")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CALENDAR_MANAGE')
            """)
    public ResponseEntity<List<AcademicCalendarDayResponse>> initialize(
            @PathVariable @Positive long academicYearId,
            @Valid @RequestBody InitializeAcademicCalendarRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.initialize(
                        organizationId(authentication),
                        academicYearId,
                        authenticatedUserId(authentication),
                        request
                )
        );
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CALENDAR_VIEW')
            """)
    public ResponseEntity<List<AcademicCalendarDayResponse>> getDays(
            @PathVariable @Positive long academicYearId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getDays(
                        organizationId(authentication),
                        academicYearId,
                        startDate,
                        endDate
                )
        );
    }

    @GetMapping("/{calendarDayId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CALENDAR_VIEW')
            """)
    public ResponseEntity<AcademicCalendarDayResponse> getDay(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long calendarDayId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getDay(
                        organizationId(authentication),
                        academicYearId,
                        calendarDayId
                )
        );
    }

    @PutMapping("/{calendarDayId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CALENDAR_MANAGE')
            """)
    public ResponseEntity<AcademicCalendarDayResponse> updateDay(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long calendarDayId,
            @Valid @RequestBody UpdateAcademicCalendarDayRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.updateDay(
                        organizationId(authentication),
                        academicYearId,
                        calendarDayId,
                        authenticatedUserId(authentication),
                        request
                )
        );
    }

    private static long organizationId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.organizationId(authentication);
    }

    private static long authenticatedUserId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.userId(authentication);
    }
}
