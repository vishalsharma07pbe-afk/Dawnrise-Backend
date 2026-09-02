package com.dawnrise.academic.section.controller;

import com.dawnrise.academic.section.dto.CreateSectionRequest;
import com.dawnrise.academic.section.dto.SectionResponse;
import com.dawnrise.academic.section.dto.UpdateSectionRequest;
import com.dawnrise.academic.section.dto.BulkCreateSectionsRequest;
import com.dawnrise.academic.section.service.SectionService;
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
@RequestMapping(
        "/api/v1/academic-years/{academicYearId}"
                + "/grade-levels/{gradeLevelId}/sections"
)
@Validated
public class SectionController {

    private final SectionService sectionService;

    public SectionController(
            SectionService sectionService
    ) {
        this.sectionService = sectionService;
    }

    @PostMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SECTION_CREATE')
            """)
    public ResponseEntity<SectionResponse> create(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @PathVariable
            @Positive(message = "Grade level ID must be positive")
            long gradeLevelId,
            @Valid @RequestBody CreateSectionRequest request,
            JwtAuthenticationToken authentication
    ) {
        SectionResponse response = sectionService.create(
                organizationId(authentication),
                academicYearId,
                gradeLevelId,
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/bulk")
    @PreAuthorize("""
        @academicTenantSecurity.hasOrganization(authentication)
        and hasAuthority('SECTION_CREATE')
        """)
    public ResponseEntity<List<SectionResponse>> createBulk(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @PathVariable
            @Positive(message = "Grade level ID must be positive")
            long gradeLevelId,
            @Valid @RequestBody BulkCreateSectionsRequest request,
            JwtAuthenticationToken authentication
    ) {
        List<SectionResponse> responses =
                sectionService.createBulk(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SECTION_VIEW')
            """)
    public ResponseEntity<List<SectionResponse>> getAll(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @PathVariable
            @Positive(message = "Grade level ID must be positive")
            long gradeLevelId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                sectionService.getAll(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId
                )
        );
    }

    @GetMapping("/{sectionId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SECTION_VIEW')
            """)
    public ResponseEntity<SectionResponse> getById(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @PathVariable
            @Positive(message = "Grade level ID must be positive")
            long gradeLevelId,
            @PathVariable
            @Positive(message = "Section ID must be positive")
            long sectionId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                sectionService.getById(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId
                )
        );
    }

    @PutMapping("/{sectionId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SECTION_UPDATE')
            """)
    public ResponseEntity<SectionResponse> update(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @PathVariable
            @Positive(message = "Grade level ID must be positive")
            long gradeLevelId,
            @PathVariable
            @Positive(message = "Section ID must be positive")
            long sectionId,
            @Valid @RequestBody UpdateSectionRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                sectionService.update(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
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