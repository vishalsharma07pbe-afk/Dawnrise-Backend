package com.dawnrise.academic.examination.controller;

import com.dawnrise.academic.examination.dto.CreateExaminationRequest;
import com.dawnrise.academic.examination.dto.CreateScheduledAssessmentRequest;
import com.dawnrise.academic.examination.dto.ExaminationResponse;
import com.dawnrise.academic.examination.dto.UpdateExaminationRequest;
import com.dawnrise.academic.examination.dto.UpdateScheduledAssessmentRequest;
import com.dawnrise.academic.examination.dto.VersionRequest;
import com.dawnrise.academic.examination.service.ExaminationService;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/academic-years/{academicYearId}/examinations")
@Validated
public class ExaminationController {
    private final ExaminationService service;

    public ExaminationController(ExaminationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_MANAGE')")
    public ResponseEntity<ExaminationResponse> create(
            @PathVariable @Positive long academicYearId,
            @Valid @RequestBody CreateExaminationRequest request,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(org(authentication), academicYearId, request));
    }

    @GetMapping
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_VIEW')")
    public List<ExaminationResponse> list(
            @PathVariable @Positive long academicYearId,
            JwtAuthenticationToken authentication) {
        return service.list(org(authentication), academicYearId, canManage(authentication));
    }

    @GetMapping("/{examinationId}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_VIEW')")
    public ExaminationResponse get(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long examinationId,
            JwtAuthenticationToken authentication) {
        return service.get(org(authentication), academicYearId,
                examinationId, canManage(authentication));
    }

    @PutMapping("/{examinationId}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_MANAGE')")
    public ExaminationResponse rename(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long examinationId,
            @Valid @RequestBody UpdateExaminationRequest request,
            JwtAuthenticationToken authentication) {
        return service.rename(org(authentication), academicYearId, examinationId, request);
    }

    @PostMapping("/{examinationId}/publish")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_MANAGE')")
    public ExaminationResponse publish(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long examinationId,
            @Valid @RequestBody VersionRequest request,
            JwtAuthenticationToken authentication) {
        return service.publish(org(authentication), academicYearId, examinationId, request);
    }

    @PostMapping("/{examinationId}/cancel")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_MANAGE')")
    public ExaminationResponse cancel(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long examinationId,
            @Valid @RequestBody VersionRequest request,
            JwtAuthenticationToken authentication) {
        return service.cancel(org(authentication), academicYearId, examinationId, request);
    }

    @PostMapping("/{examinationId}/assessments")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_MANAGE')")
    public ResponseEntity<ExaminationResponse> addAssessment(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long examinationId,
            @Valid @RequestBody CreateScheduledAssessmentRequest request,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addAssessment(
                org(authentication), academicYearId, examinationId, request));
    }

    @PutMapping("/{examinationId}/assessments/{assessmentId}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_MANAGE')")
    public ExaminationResponse updateAssessment(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long examinationId,
            @PathVariable @Positive long assessmentId,
            @Valid @RequestBody UpdateScheduledAssessmentRequest request,
            JwtAuthenticationToken authentication) {
        return service.updateAssessment(org(authentication), academicYearId,
                examinationId, assessmentId, request);
    }

    @DeleteMapping("/{examinationId}/assessments/{assessmentId}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('EXAMINATION_SCHEDULE_MANAGE')")
    public ResponseEntity<Void> removeAssessment(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long examinationId,
            @PathVariable @Positive long assessmentId,
            @RequestParam @PositiveOrZero long version,
            JwtAuthenticationToken authentication) {
        service.removeAssessment(org(authentication), academicYearId,
                examinationId, assessmentId, version);
        return ResponseEntity.noContent().build();
    }

    private static long org(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.organizationId(authentication);
    }

    private static boolean canManage(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream().anyMatch(
                a -> "EXAMINATION_SCHEDULE_MANAGE".equals(a.getAuthority()));
    }
}
