package com.dawnrise.academic.results.controller;

import com.dawnrise.academic.results.dto.ResultSheetVersionRequest;
import com.dawnrise.academic.results.dto.ResultSubjectSheetResponse;
import com.dawnrise.academic.results.dto.ResultWorklistTaskResponse;
import com.dawnrise.academic.results.dto.ReturnResultSubjectSheetRequest;
import com.dawnrise.academic.results.dto.SaveResultSubjectMarksRequest;
import com.dawnrise.academic.results.service.ResultSubjectSheetService;
import com.dawnrise.academic.results.service.ResultWorklistService;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/results")
public class ResultSubjectSheetController {
    private final ResultSubjectSheetService service;
    private final ResultWorklistService worklistService;

    public ResultSubjectSheetController(ResultSubjectSheetService service,
                                        ResultWorklistService worklistService) {
        this.service = service;
        this.worklistService = worklistService;
    }

    @GetMapping("/worklist")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_VIEW')")
    public List<ResultWorklistTaskResponse> worklist(
            JwtAuthenticationToken authentication) {
        return worklistService.list(organizationId(authentication),
                actorUserId(authentication),
                hasAuthority(authentication, "RESULT_MARK_ENTRY"),
                hasAuthority(authentication, "RESULT_REVIEW"));
    }

    @PostMapping("/academic-years/{academicYearId}/examinations/{examinationId}/assessments/{scheduledAssessmentId}/sections/{sectionId}/subject-sheet")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_MARK_ENTRY')")
    public ResponseEntity<ResultSubjectSheetResponse> getOrCreate(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long examinationId,
            @PathVariable @Positive long scheduledAssessmentId,
            @PathVariable @Positive long sectionId,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.getOrCreate(
                organizationId(authentication), actorUserId(authentication),
                academicYearId, examinationId, scheduledAssessmentId, sectionId));
    }

    @GetMapping("/subject-sheets/{subjectSheetId}")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_VIEW')")
    public ResultSubjectSheetResponse get(
            @PathVariable @Positive long subjectSheetId,
            JwtAuthenticationToken authentication) {
        return service.get(organizationId(authentication),
                actorUserId(authentication), subjectSheetId);
    }

    @PutMapping("/subject-sheets/{subjectSheetId}/marks")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_MARK_ENTRY')")
    public ResultSubjectSheetResponse saveMarks(
            @PathVariable @Positive long subjectSheetId,
            @Valid @RequestBody SaveResultSubjectMarksRequest request,
            JwtAuthenticationToken authentication) {
        return service.saveMarks(organizationId(authentication),
                actorUserId(authentication), subjectSheetId, request);
    }

    @PostMapping("/subject-sheets/{subjectSheetId}/submit")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_MARK_ENTRY')")
    public ResultSubjectSheetResponse submit(
            @PathVariable @Positive long subjectSheetId,
            @Valid @RequestBody ResultSheetVersionRequest request,
            JwtAuthenticationToken authentication) {
        return service.submit(organizationId(authentication),
                actorUserId(authentication), subjectSheetId, request);
    }

    @PostMapping("/subject-sheets/{subjectSheetId}/return")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_REVIEW')")
    public ResultSubjectSheetResponse returnForCorrection(
            @PathVariable @Positive long subjectSheetId,
            @Valid @RequestBody ReturnResultSubjectSheetRequest request,
            JwtAuthenticationToken authentication) {
        return service.returnForCorrection(organizationId(authentication),
                actorUserId(authentication), subjectSheetId, request);
    }

    @PostMapping("/subject-sheets/{subjectSheetId}/approve")
    @PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('RESULT_REVIEW')")
    public ResultSubjectSheetResponse approve(
            @PathVariable @Positive long subjectSheetId,
            @Valid @RequestBody ResultSheetVersionRequest request,
            JwtAuthenticationToken authentication) {
        return service.approve(organizationId(authentication),
                actorUserId(authentication), subjectSheetId, request);
    }

    private static long organizationId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.organizationId(authentication);
    }

    private static long actorUserId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.userId(authentication);
    }

    private static boolean hasAuthority(JwtAuthenticationToken authentication,
                                        String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority ->
                        authority.equals(grantedAuthority.getAuthority()));
    }
}
