package com.dawnrise.academic.timetable.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.GenerateRequest;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.JobResponse;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.CancelRequest;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.RetryRequest;
import com.dawnrise.academic.timetable.service.TimetableGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/timetable-generation-jobs")
public class TimetableGenerationController {
    private static final String VIEW="@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('TIMETABLE_DRAFT_VIEW')";
    private static final String GENERATE="@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('TIMETABLE_GENERATE')";
    private final TimetableGenerationService service;
    public TimetableGenerationController(TimetableGenerationService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize(GENERATE)
    public JobResponse submit(@Valid @RequestBody GenerateRequest request,JwtAuthenticationToken auth){return service.submit(org(auth),actor(auth),request);}
    @GetMapping("/{jobId}") @PreAuthorize(VIEW)
    public JobResponse get(@PathVariable @Positive long jobId,JwtAuthenticationToken auth){return service.get(org(auth),jobId);}
    @PostMapping("/{jobId}/cancel") @PreAuthorize(GENERATE)
    public JobResponse cancel(@PathVariable @Positive long jobId,@Valid @RequestBody CancelRequest request,JwtAuthenticationToken auth){return service.cancel(org(auth),jobId,request.expectedVersion());}
    @PostMapping("/{jobId}/retry") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize(GENERATE)
    public JobResponse retry(@PathVariable @Positive long jobId,@Valid @RequestBody RetryRequest request,JwtAuthenticationToken auth){return service.retry(org(auth),actor(auth),jobId,request);}
    private long org(JwtAuthenticationToken auth){return AuthenticatedAcademicActor.organizationId(auth);}
    private long actor(JwtAuthenticationToken auth){return AuthenticatedAcademicActor.userId(auth);}
}
