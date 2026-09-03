package com.dawnrise.academic.subject.controller;

import com.dawnrise.academic.subject.dto.BulkCreateSubjectsRequest;
import com.dawnrise.academic.subject.dto.CreateSubjectRequest;
import com.dawnrise.academic.subject.dto.SubjectResponse;
import com.dawnrise.academic.subject.dto.UpdateSubjectRequest;
import com.dawnrise.academic.subject.service.SubjectService;
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
        "/api/v1/academic-years/{academicYearId}/subjects"
)
@Validated
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SUBJECT_CREATE')
            """)
    public ResponseEntity<SubjectResponse> create(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @Valid @RequestBody CreateSubjectRequest request,
            JwtAuthenticationToken authentication
    ) {
        SubjectResponse response = subjectService.create(
                organizationId(authentication),
                academicYearId,
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/bulk")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SUBJECT_CREATE')
            """)
    public ResponseEntity<List<SubjectResponse>> createBulk(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @Valid @RequestBody BulkCreateSubjectsRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(subjectService.createBulk(
                        organizationId(authentication),
                        academicYearId,
                        request
                ));
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SUBJECT_VIEW')
            """)
    public ResponseEntity<List<SubjectResponse>> getAll(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                subjectService.getAll(
                        organizationId(authentication),
                        academicYearId
                )
        );
    }

    @GetMapping("/{subjectId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SUBJECT_VIEW')
            """)
    public ResponseEntity<SubjectResponse> getById(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @PathVariable
            @Positive(message = "Subject ID must be positive")
            long subjectId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                subjectService.getById(
                        organizationId(authentication),
                        academicYearId,
                        subjectId
                )
        );
    }

    @PutMapping("/{subjectId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SUBJECT_UPDATE')
            """)
    public ResponseEntity<SubjectResponse> update(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @PathVariable
            @Positive(message = "Subject ID must be positive")
            long subjectId,
            @Valid @RequestBody UpdateSubjectRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                subjectService.update(
                        organizationId(authentication),
                        academicYearId,
                        subjectId,
                        request
                )
        );
    }

    private static long organizationId(
            JwtAuthenticationToken authentication
    ) {
        Number organizationId =
                authentication.getToken().getClaim("organizationId");

        return organizationId.longValue();
    }
}
