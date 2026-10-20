package com.dawnrise.academic.grading.controller;

import com.dawnrise.academic.grading.dto.*;
import com.dawnrise.academic.grading.service.*;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/grading-policies")
public class GradingPolicyController {
    private final GradingPolicyService service;

    public GradingPolicyController(GradingPolicyService s) {
        service = s;
    }

    @GetMapping
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('GRADING_POLICY_VIEW')")
    public List<GradingPolicyResponse> list(@RequestParam @Positive long academicYearId, @RequestParam @Positive long gradeLevelId, JwtAuthenticationToken a) {
        return service.list(org(a), academicYearId, gradeLevelId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('GRADING_POLICY_VIEW')")
    public GradingPolicyResponse detail(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return service.detail(org(a), id);
    }

    @PostMapping
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('GRADING_POLICY_MANAGE')")
    public GradingPolicyResponse create(@Valid @RequestBody GradingPolicyRequest r, JwtAuthenticationToken a) {
        return service.create(org(a), user(a), r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('GRADING_POLICY_MANAGE')")
    public GradingPolicyResponse update(@PathVariable @Positive long id, @Valid @RequestBody GradingPolicyRequest r, JwtAuthenticationToken a) {
        return service.update(org(a), user(a), id, r);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('GRADING_POLICY_MANAGE')")
    public GradingPolicyResponse activate(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return service.activate(org(a), user(a), id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('GRADING_POLICY_MANAGE')")
    public void deleteDraft(@PathVariable @Positive long id,
                            @RequestParam @PositiveOrZero long version,
                            JwtAuthenticationToken a) {
        service.deleteDraft(org(a), id, version);
    }

    private static long org(JwtAuthenticationToken a) {
        return AuthenticatedAcademicActor.organizationId(a);
    }

    private static long user(JwtAuthenticationToken a) {
        return AuthenticatedAcademicActor.userId(a);
    }
}
