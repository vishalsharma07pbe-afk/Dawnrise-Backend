package com.dawnrise.academic.gradelevelsubject.controller;

import com.dawnrise.academic.gradelevelsubject.dto.CreateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.dto.GradeLevelSubjectResponse;
import com.dawnrise.academic.gradelevelsubject.dto.UpdateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.service.GradeLevelSubjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/academic-years/{academicYearId}"
                + "/grade-levels/{gradeLevelId}/subjects"
)
@Validated
public class GradeLevelSubjectController {

    private final GradeLevelSubjectService assignmentService;

    public GradeLevelSubjectController(
            GradeLevelSubjectService assignmentService
    ) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_SUBJECT_ASSIGN')
            """)
    public ResponseEntity<GradeLevelSubjectResponse> assign(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @Valid @RequestBody CreateGradeLevelSubjectRequest request,
            JwtAuthenticationToken authentication
    ) {
        GradeLevelSubjectResponse response = assignmentService.assign(
                organizationId(authentication),
                academicYearId,
                gradeLevelId,
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_SUBJECT_VIEW')
            """)
    public ResponseEntity<List<GradeLevelSubjectResponse>> getAll(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                assignmentService.getAll(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId
                )
        );
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_SUBJECT_VIEW')
            """)
    public ResponseEntity<GradeLevelSubjectResponse> getById(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long assignmentId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                assignmentService.getById(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        assignmentId
                )
        );
    }

    @PutMapping("/{assignmentId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_SUBJECT_UPDATE')
            """)
    public ResponseEntity<GradeLevelSubjectResponse> update(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long assignmentId,
            @Valid @RequestBody UpdateGradeLevelSubjectRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                assignmentService.update(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        assignmentId,
                        request
                )
        );
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_SUBJECT_REMOVE')
            """)
    public ResponseEntity<Void> remove(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long assignmentId,
            JwtAuthenticationToken authentication
    ) {
        assignmentService.remove(
                organizationId(authentication),
                academicYearId,
                gradeLevelId,
                assignmentId
        );

        return ResponseEntity.noContent().build();
    }

    private static long organizationId(
            JwtAuthenticationToken authentication
    ) {
        Number organizationId =
                authentication.getToken().getClaim("organizationId");

        return organizationId.longValue();
    }
}