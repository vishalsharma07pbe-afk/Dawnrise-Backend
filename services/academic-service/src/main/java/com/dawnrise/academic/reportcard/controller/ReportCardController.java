package com.dawnrise.academic.reportcard.controller;

import com.dawnrise.academic.reportcard.dto.*;
import com.dawnrise.academic.reportcard.service.*;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/report-cards")
public class ReportCardController {
    private final ReportCardService service;
    private final ReportCardPdfService pdf;

    public ReportCardController(ReportCardService s, ReportCardPdfService p) {
        service = s;
        pdf = p;
    }

    @GetMapping("/publications/{id}/readiness")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('REPORT_CARD_GENERATE')")
    public ReportCardReadinessResponse readiness(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return service.readiness(org(a), id);
    }

    @PostMapping("/publications/{id}/generate")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('REPORT_CARD_GENERATE')")
    public List<ReportCardResponse> generate(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return service.generate(org(a), user(a), id);
    }

    @GetMapping("/publications/{id}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and (hasAuthority('REPORT_CARD_GENERATE') or hasAuthority('REPORT_CARD_REVIEW'))")
    public List<ReportCardResponse> list(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return service.list(org(a), id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and (hasAuthority('REPORT_CARD_GENERATE') or hasAuthority('REPORT_CARD_REVIEW'))")
    public ReportCardResponse detail(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return service.detail(org(a), id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('REPORT_CARD_GENERATE')")
    public ReportCardResponse submit(@PathVariable @Positive long id, @Valid @RequestBody ReportCardVersionRequest r, JwtAuthenticationToken a) {
        return service.submit(org(a), id, r.version());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('REPORT_CARD_REVIEW')")
    public ReportCardResponse approve(@PathVariable @Positive long id, @Valid @RequestBody ReportCardVersionRequest r, JwtAuthenticationToken a) {
        return service.approve(org(a), user(a), id, r.version());
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('REPORT_CARD_REVIEW')")
    public ReportCardResponse back(@PathVariable @Positive long id, @Valid @RequestBody ReturnReportCardRequest r, JwtAuthenticationToken a) {
        return service.returnToDraft(org(a), user(a), id, r.version(), r.reason());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('REPORT_CARD_PUBLISH')")
    public ReportCardResponse publish(@PathVariable @Positive long id, @Valid @RequestBody ReportCardVersionRequest r, JwtAuthenticationToken a) {
        return service.publish(org(a), user(a), id, r.version());
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and (hasAuthority('REPORT_CARD_GENERATE') or hasAuthority('REPORT_CARD_REVIEW'))")
    public ResponseEntity<byte[]> pdf(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return file(service.detail(org(a), id));
    }

    @GetMapping("/published/self")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('STUDENT') and hasAuthority('REPORT_CARD_VIEW_SELF')")
    public List<ReportCardResponse> self(JwtAuthenticationToken a) {
        return service.self(org(a), user(a));
    }

    @GetMapping("/published/self/{id}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('STUDENT') and hasAuthority('REPORT_CARD_VIEW_SELF')")
    public ReportCardResponse selfDetail(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return service.selfDetail(org(a), user(a), id);
    }

    @GetMapping("/published/self/{id}/pdf")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('STUDENT') and hasAuthority('REPORT_CARD_VIEW_SELF')")
    public ResponseEntity<byte[]> selfPdf(@PathVariable @Positive long id, JwtAuthenticationToken a) {
        return file(service.selfDetail(org(a), user(a), id));
    }

    @GetMapping("/published/parent/students/{student}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('PARENT') and hasAuthority('REPORT_CARD_VIEW_SELF')")
    public List<ReportCardResponse> parent(@PathVariable @Positive long student, JwtAuthenticationToken a) {
        return service.parent(org(a), user(a), student);
    }

    @GetMapping("/published/parent/students/{student}/{id}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('PARENT') and hasAuthority('REPORT_CARD_VIEW_SELF')")
    public ReportCardResponse parentDetail(@PathVariable @Positive long student, @PathVariable @Positive long id, JwtAuthenticationToken a) {
        return service.parentDetail(org(a), user(a), student, id);
    }

    @GetMapping("/published/parent/students/{student}/{id}/pdf")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasRole('PARENT') and hasAuthority('REPORT_CARD_VIEW_SELF')")
    public ResponseEntity<byte[]> parentPdf(@PathVariable @Positive long student, @PathVariable @Positive long id, JwtAuthenticationToken a) {
        return file(service.parentDetail(org(a), user(a), student, id));
    }

    private ResponseEntity<byte[]> file(ReportCardResponse c) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.filename(c) + "\"").body(pdf.create(c));
    }

    private static long org(JwtAuthenticationToken a) {
        return AuthenticatedAcademicActor.organizationId(a);
    }

    private static long user(JwtAuthenticationToken a) {
        return AuthenticatedAcademicActor.userId(a);
    }
}
