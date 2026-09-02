package com.dawnrise.academic.gradelevel.controller;

import com.dawnrise.academic.gradelevel.dto.CreateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.dto.GradeLevelResponse;
import com.dawnrise.academic.gradelevel.dto.UpdateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.service.GradeLevelService;
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
        "/api/v1/academic-years/{academicYearId}/grade-levels"
)
@Validated
public class GradeLevelController {

    private final GradeLevelService gradeLevelService;

    public GradeLevelController(
            GradeLevelService gradeLevelService
    ) {
        this.gradeLevelService = gradeLevelService;
    }

    @PostMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_LEVEL_CREATE')
            """)
    public ResponseEntity<GradeLevelResponse> create(
            @PathVariable @Positive long academicYearId,
            @Valid @RequestBody CreateGradeLevelRequest request,
            JwtAuthenticationToken authentication
    ) {
        GradeLevelResponse response = gradeLevelService.create(
                organizationId(authentication),
                academicYearId,
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_LEVEL_VIEW')
            """)
    public ResponseEntity<List<GradeLevelResponse>> getAll(
            @PathVariable @Positive long academicYearId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                gradeLevelService.getAll(
                        organizationId(authentication),
                        academicYearId
                )
        );
    }

    @GetMapping("/{gradeLevelId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_LEVEL_VIEW')
            """)
    public ResponseEntity<GradeLevelResponse> getById(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                gradeLevelService.getById(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId
                )
        );
    }

    @PutMapping("/{gradeLevelId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_LEVEL_UPDATE')
            """)
    public ResponseEntity<GradeLevelResponse> update(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @Valid @RequestBody UpdateGradeLevelRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                gradeLevelService.update(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
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
