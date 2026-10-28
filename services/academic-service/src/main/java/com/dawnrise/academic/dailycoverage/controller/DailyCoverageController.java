package com.dawnrise.academic.dailycoverage.controller;

import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.AffectedPeriod;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.Candidate;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.CandidateChoiceRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.CorrectAbsenceRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.CoverageAlert;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ManualPreview;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.MutationResponse;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ProposalSummary;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ReasonedVersionRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.RecordAbsenceRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.VersionRequest;
import com.dawnrise.academic.dailycoverage.service.DailyCoverageService;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/daily-timetable-cover")
public class DailyCoverageController {
    private static final String TENANT = "@academicTenantSecurity.hasOrganization(authentication)";
    private static final String VIEW = TENANT + " and hasAuthority('DAILY_COVERAGE_VIEW')";
    private static final String MANAGE = TENANT + " and hasAuthority('DAILY_COVERAGE_MANAGE')";
    private static final String APPROVE = TENANT + " and hasAuthority('DAILY_COVERAGE_APPROVE')";
    private final DailyCoverageService service;

    public DailyCoverageController(DailyCoverageService service) { this.service = service; }

    @PostMapping("/absences") @PreAuthorize(MANAGE)
    public MutationResponse absence(@Valid @RequestBody RecordAbsenceRequest request, JwtAuthenticationToken auth) {
        return service.recordAbsence(org(auth), actor(auth), request);
    }

    @PostMapping("/absences/{id}/corrections") @PreAuthorize(MANAGE)
    public MutationResponse correctAbsence(@PathVariable @Positive long id,
                                           @Valid @RequestBody CorrectAbsenceRequest request,
                                           JwtAuthenticationToken auth) {
        return service.correctAbsence(org(auth), actor(auth), id, request);
    }

    @PostMapping("/absences/{id}/cancellations") @PreAuthorize(MANAGE)
    public ResponseEntity<Void> cancelAbsence(@PathVariable @Positive long id,
                                               @Valid @RequestBody ReasonedVersionRequest request,
                                               JwtAuthenticationToken auth) {
        service.cancelAbsence(org(auth), actor(auth), id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/academic-years/{year}/dates/{date}/cases") @PreAuthorize(VIEW)
    public List<AffectedPeriod> cases(@PathVariable @Positive long year, @PathVariable LocalDate date,
                                      @RequestParam(required = false) Long wingId,
                                      @RequestParam(required = false) String status,
                                      JwtAuthenticationToken auth) {
        List<AffectedPeriod> values = service.cases(org(auth), year, date, wingId, status);
        boolean audit = auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("DAILY_COVERAGE_AUDIT_VIEW"));
        return audit ? values : values.stream().map(AffectedPeriod::withoutHistory).toList();
    }

    @GetMapping("/academic-years/{year}/dates/{date}/alerts") @PreAuthorize(VIEW)
    public List<CoverageAlert> alerts(@PathVariable @Positive long year, @PathVariable LocalDate date,
                                      JwtAuthenticationToken auth) {
        return service.alerts(org(auth), year, date);
    }

    @GetMapping("/cases/{id}/candidates") @PreAuthorize(VIEW)
    public List<Candidate> candidates(@PathVariable @Positive long id, JwtAuthenticationToken auth) {
        return service.candidates(org(auth), id);
    }

    @PostMapping("/cases/{id}/manual-preview") @PreAuthorize(MANAGE)
    public ManualPreview preview(@PathVariable @Positive long id,
                                 @Valid @RequestBody CandidateChoiceRequest request,
                                 JwtAuthenticationToken auth) {
        return service.preview(org(auth), id, request);
    }

    @PostMapping("/cases/{id}/manual-proposals") @PreAuthorize(MANAGE)
    public ProposalSummary manual(@PathVariable @Positive long id,
                                  @Valid @RequestBody CandidateChoiceRequest request,
                                  JwtAuthenticationToken auth) {
        return service.manualProposal(org(auth), actor(auth), id, request);
    }

    @PostMapping("/cases/{id}/autonomous-proposals") @PreAuthorize(MANAGE)
    public ProposalSummary autonomous(@PathVariable @Positive long id,
                                      @Valid @RequestBody VersionRequest request,
                                      JwtAuthenticationToken auth) {
        return service.autonomousProposal(org(auth), actor(auth), id, request);
    }

    @PostMapping("/proposals/{id}/approve") @PreAuthorize(APPROVE)
    public ProposalSummary approve(@PathVariable @Positive long id,
                                   @Valid @RequestBody VersionRequest request,
                                   JwtAuthenticationToken auth) {
        return service.approve(org(auth), actor(auth), id, request);
    }

    @PostMapping("/proposals/{id}/reject") @PreAuthorize(APPROVE)
    public ProposalSummary reject(@PathVariable @Positive long id,
                                  @Valid @RequestBody ReasonedVersionRequest request,
                                  JwtAuthenticationToken auth) {
        return service.reject(org(auth), actor(auth), id, request);
    }

    @PostMapping("/cases/{id}/changes") @PreAuthorize(MANAGE)
    public ProposalSummary change(@PathVariable @Positive long id,
                                  @Valid @RequestBody CandidateChoiceRequest request,
                                  JwtAuthenticationToken auth) {
        return service.change(org(auth), actor(auth), id, request, false);
    }

    @PostMapping("/cases/{id}/corrections") @PreAuthorize(MANAGE)
    public ProposalSummary correct(@PathVariable @Positive long id,
                                   @Valid @RequestBody CandidateChoiceRequest request,
                                   JwtAuthenticationToken auth) {
        return service.change(org(auth), actor(auth), id, request, true);
    }

    @PostMapping("/cases/{id}/cancellations") @PreAuthorize(MANAGE)
    public ResponseEntity<Void> cancel(@PathVariable @Positive long id,
                                       @Valid @RequestBody ReasonedVersionRequest request,
                                       JwtAuthenticationToken auth) {
        service.cancelCover(org(auth), actor(auth), id, request);
        return ResponseEntity.noContent().build();
    }

    private long org(JwtAuthenticationToken auth) { return AuthenticatedAcademicActor.organizationId(auth); }
    private long actor(JwtAuthenticationToken auth) { return AuthenticatedAcademicActor.userId(auth); }
}
