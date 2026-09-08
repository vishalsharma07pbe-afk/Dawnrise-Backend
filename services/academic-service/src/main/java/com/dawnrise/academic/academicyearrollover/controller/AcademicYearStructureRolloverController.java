package com.dawnrise.academic.academicyearrollover.controller;

import com.dawnrise.academic.academicyearrollover.dto.*;
import com.dawnrise.academic.academicyearrollover.service.RolloverConfirmation;
import com.dawnrise.academic.academicyearrollover.service.AcademicYearStructureRolloverService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class AcademicYearStructureRolloverController {

    private final AcademicYearStructureRolloverService rolloverService;

    public AcademicYearStructureRolloverController(
            AcademicYearStructureRolloverService rolloverService
    ) {
        this.rolloverService = rolloverService;
    }

    @PostMapping(
            "/api/v1/academic-years/{targetAcademicYearId}"
                    + "/structure-rollovers/preview"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_STRUCTURE_ROLLOVER')
            """)
    public ResponseEntity<AcademicYearStructureRolloverPreviewResponse> preview(
            @PathVariable @Positive long targetAcademicYearId,
            @Valid @RequestBody AcademicYearStructureRolloverRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(rolloverService.preview(
                organizationId(authentication),
                targetAcademicYearId,
                request
        ));
    }

    @PostMapping(
            "/api/v1/academic-years/{targetAcademicYearId}"
                    + "/structure-rollovers"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_STRUCTURE_ROLLOVER')
            """)
    public ResponseEntity<AcademicYearStructureRolloverResultResponse> confirm(
            @PathVariable @Positive long targetAcademicYearId,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody ConfirmAcademicYearStructureRolloverRequest request,
            JwtAuthenticationToken authentication
    ) {
        RolloverConfirmation confirmation =
                rolloverService.confirm(
                        organizationId(authentication),
                        authenticatedUserId(authentication),
                        targetAcademicYearId,
                        idempotencyKey,
                        request
                );
        return ResponseEntity
                .status(confirmation.replay()
                        ? HttpStatus.OK
                        : HttpStatus.CREATED)
                .body(confirmation.response());
    }

    @GetMapping("/api/v1/academic-year-structure-rollovers/{operationId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('ACADEMIC_YEAR_STRUCTURE_ROLLOVER')
            """)
    public ResponseEntity<AcademicYearStructureRolloverOperationResponse>
    getOperation(
            @PathVariable @Positive long operationId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(rolloverService.getOperation(
                organizationId(authentication),
                operationId
        ));
    }

    private static long organizationId(
            JwtAuthenticationToken authentication
    ) {
        Number organizationId =
                authentication.getToken().getClaim("organizationId");

        return organizationId.longValue();
    }

    private static long authenticatedUserId(
            JwtAuthenticationToken authentication
    ) {
        String subject = authentication.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException(
                    "Authenticated token does not contain a user ID"
            );
        }
        return Long.parseLong(subject);
    }
}
