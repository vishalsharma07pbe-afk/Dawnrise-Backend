package com.dawnrise.academic.dailycoverage.controller;

import com.dawnrise.academic.dailycoverage.service.DailyCoverIncidentService;
import com.dawnrise.academic.dailycoverage.service.DailyCoverIncidentService.Incident;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/daily-timetable-cover/incidents")
@PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and "
        + "hasAnyAuthority('DAILY_COVERAGE_MANAGE','DAILY_COVERAGE_APPROVE','DAILY_COVERAGE_AUDIT_VIEW')")
public class DailyCoverIncidentController {
    private final DailyCoverIncidentService incidents;

    public DailyCoverIncidentController(DailyCoverIncidentService incidents) {
        this.incidents = incidents;
    }

    @GetMapping
    public List<Incident> open(JwtAuthenticationToken authentication) {
        return incidents.open(AuthenticatedAcademicActor.organizationId(authentication));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable @Positive long id,
                                             JwtAuthenticationToken authentication) {
        boolean changed = incidents.acknowledge(AuthenticatedAcademicActor.organizationId(authentication), id,
                AuthenticatedAcademicActor.userId(authentication));
        return changed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
