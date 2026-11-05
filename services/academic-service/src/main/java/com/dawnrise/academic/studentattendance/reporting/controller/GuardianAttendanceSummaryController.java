package com.dawnrise.academic.studentattendance.reporting.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.studentattendance.reporting.dto.GuardianAttendanceSummaryResponse;
import com.dawnrise.academic.studentattendance.reporting.service.GuardianAttendanceSummaryService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student-attendance/guardian")
@Validated
public class GuardianAttendanceSummaryController {
    private final GuardianAttendanceSummaryService service;

    public GuardianAttendanceSummaryController(GuardianAttendanceSummaryService service) {
        this.service = service;
    }

    @GetMapping("/students/{studentUserId}/summary")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('STUDENT_ATTENDANCE_GUARDIAN_VIEW')")
    public GuardianAttendanceSummaryResponse summary(@PathVariable @Positive long studentUserId,
                                                      JwtAuthenticationToken authentication) {
        return service.get(AuthenticatedAcademicActor.organizationId(authentication),
                AuthenticatedAcademicActor.userId(authentication), studentUserId);
    }
}
