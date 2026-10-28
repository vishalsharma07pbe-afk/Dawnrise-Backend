package com.dawnrise.academic.dailycoverage.controller;

import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.DailyTimetable;
import com.dawnrise.academic.dailycoverage.service.DailyCoverageService;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/v1/timetable-days/academic-years/{year}")
public class DailyTimetableController {
    private static final String TENANT = "@academicTenantSecurity.hasOrganization(authentication)";
    private static final String STAFF = TENANT + " and hasAuthority('TIMETABLE_PUBLISHED_VIEW')";
    private final DailyCoverageService service;

    public DailyTimetableController(DailyCoverageService service) { this.service = service; }

    @GetMapping("/school") @PreAuthorize(STAFF)
    public DailyTimetable school(@PathVariable @Positive long year, @RequestParam(required = false) LocalDate date,
                                 JwtAuthenticationToken auth) {
        return service.daily(org(auth), year, date, null, null, null, null);
    }

    @GetMapping("/wings/{wing}") @PreAuthorize(STAFF)
    public DailyTimetable wing(@PathVariable @Positive long year, @PathVariable @Positive long wing,
                               @RequestParam(required = false) LocalDate date, JwtAuthenticationToken auth) {
        return service.daily(org(auth), year, date, wing, null, null, null);
    }

    @GetMapping("/grades/{grade}") @PreAuthorize(STAFF)
    public DailyTimetable grade(@PathVariable @Positive long year, @PathVariable @Positive long grade,
                                @RequestParam(required = false) LocalDate date, JwtAuthenticationToken auth) {
        return service.daily(org(auth), year, date, null, grade, null, null);
    }

    @GetMapping("/sections/{section}") @PreAuthorize(STAFF)
    public DailyTimetable section(@PathVariable @Positive long year, @PathVariable @Positive long section,
                                  @RequestParam(required = false) LocalDate date, JwtAuthenticationToken auth) {
        return service.daily(org(auth), year, date, null, null, section, null);
    }

    @GetMapping("/teachers/{teacher}") @PreAuthorize(STAFF)
    public DailyTimetable teacher(@PathVariable @Positive long year, @PathVariable @Positive long teacher,
                                  @RequestParam(required = false) LocalDate date, JwtAuthenticationToken auth) {
        return service.daily(org(auth), year, date, null, null, null, teacher);
    }

    @GetMapping("/teacher/self")
    @PreAuthorize(TENANT + " and hasRole('TEACHER') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public DailyTimetable teacherSelf(@PathVariable @Positive long year, @RequestParam(required = false) LocalDate date,
                                      JwtAuthenticationToken auth) {
        return service.daily(org(auth), year, date, null, null, null, actor(auth));
    }

    @GetMapping("/student/self")
    @PreAuthorize(TENANT + " and hasRole('STUDENT') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public DailyTimetable studentSelf(@PathVariable @Positive long year, @RequestParam(required = false) LocalDate date,
                                      JwtAuthenticationToken auth) {
        return service.student(org(auth), actor(auth), year, date);
    }

    @GetMapping("/parent/students/{student}")
    @PreAuthorize(TENANT + " and hasRole('PARENT') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public DailyTimetable parent(@PathVariable @Positive long year, @PathVariable @Positive long student,
                                 @RequestParam(required = false) LocalDate date, JwtAuthenticationToken auth) {
        return service.parent(org(auth), actor(auth), student, year, date);
    }

    private long org(JwtAuthenticationToken auth) { return AuthenticatedAcademicActor.organizationId(auth); }
    private long actor(JwtAuthenticationToken auth) { return AuthenticatedAcademicActor.userId(auth); }
}
