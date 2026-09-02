package com.dawnrise.academic.academicyear.controller;

import com.dawnrise.academic.academicyear.dto.AcademicYearResponse;
import com.dawnrise.academic.academicyear.dto.CreateAcademicYearRequest;
import com.dawnrise.academic.academicyear.dto.UpdateAcademicYearRequest;
import com.dawnrise.academic.academicyear.service.AcademicYearService;
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
@RequestMapping("/api/v1/academic-years")
@Validated
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    public AcademicYearController(
            AcademicYearService academicYearService
    ) {
        this.academicYearService = academicYearService;
    }

    @PostMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_CREATE')
            """)
    public ResponseEntity<AcademicYearResponse> create(
            @Valid @RequestBody CreateAcademicYearRequest request,
            JwtAuthenticationToken authentication
    ) {
        AcademicYearResponse response =
                academicYearService.create(
                        organizationId(authentication),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_VIEW')
            """)
    public ResponseEntity<List<AcademicYearResponse>> getAll(
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                academicYearService.getAll(
                        organizationId(authentication)
                )
        );
    }

    @GetMapping("/active")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_VIEW')
            """)
    public ResponseEntity<AcademicYearResponse> getActive(
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                academicYearService.getActive(
                        organizationId(authentication)
                )
        );
    }

    @GetMapping("/{academicYearId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_VIEW')
            """)
    public ResponseEntity<AcademicYearResponse> getById(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                academicYearService.getById(
                        organizationId(authentication),
                        academicYearId
                )
        );
    }

    @PutMapping("/{academicYearId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_UPDATE')
            """)
    public ResponseEntity<AcademicYearResponse> update(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @Valid @RequestBody UpdateAcademicYearRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                academicYearService.update(
                        organizationId(authentication),
                        academicYearId,
                        request
                )
        );
    }

    @PostMapping("/{academicYearId}/activate")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_ACTIVATE')
            """)
    public ResponseEntity<AcademicYearResponse> activate(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                academicYearService.activate(
                        organizationId(authentication),
                        academicYearId
                )
        );
    }

    @PostMapping("/{academicYearId}/close")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_CLOSE')
            """)
    public ResponseEntity<AcademicYearResponse> close(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                academicYearService.close(
                        organizationId(authentication),
                        academicYearId
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