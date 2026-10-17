package com.dawnrise.academic.results.controller;

import com.dawnrise.academic.results.dto.PublishedResultDetailResponse;
import com.dawnrise.academic.results.dto.PublishedResultSummaryResponse;
import com.dawnrise.academic.results.dto.ResultPublicationReadinessResponse;
import com.dawnrise.academic.results.dto.ResultPublicationResponse;
import com.dawnrise.academic.results.service.ResultPublicationService;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/v1/results")
public class ResultPublicationController {
    private final ResultPublicationService service;

    public ResultPublicationController(ResultPublicationService service) {
        this.service = service;
    }

    @GetMapping("/academic-years/{yearId}/examinations/{examinationId}/grades/{gradeId}/sections/{sectionId}/publication-readiness")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_PUBLISH')")
    public ResultPublicationReadinessResponse readiness(
            @PathVariable @Positive long yearId,
            @PathVariable @Positive long examinationId,
            @PathVariable @Positive long gradeId,
            @PathVariable @Positive long sectionId,
            JwtAuthenticationToken authentication
    ) {
        return service.readiness(
                organizationId(authentication),
                yearId,
                examinationId,
                gradeId,
                sectionId
        );
    }

    @PostMapping("/academic-years/{yearId}/examinations/{examinationId}/grades/{gradeId}/sections/{sectionId}/publish")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_PUBLISH')")
    public ResultPublicationResponse publish(
            @PathVariable @Positive long yearId,
            @PathVariable @Positive long examinationId,
            @PathVariable @Positive long gradeId,
            @PathVariable @Positive long sectionId,
            JwtAuthenticationToken authentication
    ) {
        return service.publish(
                organizationId(authentication),
                userId(authentication),
                yearId,
                examinationId,
                gradeId,
                sectionId
        );
    }

    @GetMapping("/published/self")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('STUDENT') and hasAuthority('RESULT_PUBLISHED_VIEW_SELF')")
    public List<PublishedResultSummaryResponse> self(JwtAuthenticationToken authentication) {
        return service.listForStudent(
                organizationId(authentication),
                userId(authentication)
        );
    }

    @GetMapping("/published/self/{publicationId}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('STUDENT') and hasAuthority('RESULT_PUBLISHED_VIEW_SELF')")
    public PublishedResultDetailResponse selfDetail(
            @PathVariable @Positive long publicationId,
            JwtAuthenticationToken authentication
    ) {
        return service.detailForStudent(
                organizationId(authentication),
                userId(authentication),
                publicationId
        );
    }

    @GetMapping("/published/parent/students/{studentUserId}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('PARENT') and hasAuthority('RESULT_PUBLISHED_VIEW_SELF')")
    public List<PublishedResultSummaryResponse> parent(
            @PathVariable @Positive long studentUserId,
            JwtAuthenticationToken authentication
    ) {
        return service.listForParent(
                organizationId(authentication),
                userId(authentication),
                studentUserId
        );
    }

    @GetMapping("/published/parent/students/{studentUserId}/{publicationId}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('PARENT') and hasAuthority('RESULT_PUBLISHED_VIEW_SELF')")
    public PublishedResultDetailResponse parentDetail(
            @PathVariable @Positive long studentUserId,
            @PathVariable @Positive long publicationId,
            JwtAuthenticationToken authentication
    ) {
        return service.detailForParent(
                organizationId(authentication),
                userId(authentication),
                studentUserId,
                publicationId
        );
    }

    private static long organizationId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.organizationId(authentication);
    }

    private static long userId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.userId(authentication);
    }
}
