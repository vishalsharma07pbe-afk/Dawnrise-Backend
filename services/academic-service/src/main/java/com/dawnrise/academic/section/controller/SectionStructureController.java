package com.dawnrise.academic.section.controller;

import com.dawnrise.academic.section.dto.ApplySectionStructureRequest;
import com.dawnrise.academic.section.dto.SectionResponse;
import com.dawnrise.academic.section.service.SectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/academic-years/{academicYearId}/sections"
)
@Validated
public class SectionStructureController {

    private final SectionService sectionService;

    public SectionStructureController(
            SectionService sectionService
    ) {
        this.sectionService = sectionService;
    }

    @PostMapping("/apply-structure")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('SECTION_CREATE')
            """)
    public ResponseEntity<List<SectionResponse>> applyStructure(
            @PathVariable
            @Positive(message = "Academic year ID must be positive")
            long academicYearId,
            @Valid @RequestBody ApplySectionStructureRequest request,
            JwtAuthenticationToken authentication
    ) {
        List<SectionResponse> responses =
                sectionService.applyStructure(
                        organizationId(authentication),
                        academicYearId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }

    private static long organizationId(
            JwtAuthenticationToken authentication
    ) {
        Number organizationId =
                authentication.getToken().getClaim("organizationId");

        return organizationId.longValue();
    }
}