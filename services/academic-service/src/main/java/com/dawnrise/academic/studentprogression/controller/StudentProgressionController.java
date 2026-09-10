package com.dawnrise.academic.studentprogression.controller;

import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionPreviewRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionPreviewResponse;
import com.dawnrise.academic.studentprogression.service.StudentProgressionService;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionOperationResponse;
import org.springframework.web.bind.annotation.GetMapping;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@Validated
public class StudentProgressionController {

    private final StudentProgressionService progressionService;

    public StudentProgressionController(
            StudentProgressionService progressionService
    ) {
        this.progressionService = progressionService;
    }

    @GetMapping(
            "/api/v1/student-progression-operations/{operationId}"
    )
    @PreAuthorize("""
        @academicTenantSecurity.hasOrganization(authentication)
        and hasAuthority('STUDENT_ANNUAL_PROGRESSION')
        """)
    public ResponseEntity<StudentProgressionOperationResponse>
    getOperation(
            @PathVariable @Positive long operationId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                progressionService.getOperation(
                        organizationId(authentication),
                        operationId
                )
        );
    }

    @PostMapping(
            "/api/v1/academic-years/{targetAcademicYearId}"
                    + "/student-progressions/preview"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ANNUAL_PROGRESSION')
            """)
    public ResponseEntity<StudentProgressionPreviewResponse> preview(
            @PathVariable @Positive long targetAcademicYearId,
            @Valid @RequestBody StudentProgressionPreviewRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                progressionService.preview(
                        organizationId(authentication),
                        targetAcademicYearId,
                        request
                )
        );
    }

    @PostMapping(
            "/api/v1/academic-years/{targetAcademicYearId}"
                    + "/student-progressions"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ANNUAL_PROGRESSION')
            """)
    public ResponseEntity<StudentProgressionConfirmationResponse> confirm(
            @PathVariable @Positive long targetAcademicYearId,
            @RequestHeader(
                    name = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,
            @Valid @RequestBody
            StudentProgressionConfirmationRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(progressionService.confirm(
                        organizationId(authentication),
                        targetAcademicYearId,
                        authenticatedUserId(authentication),
                        idempotencyKey,
                        request
                ));
    }

    private long organizationId(
            JwtAuthenticationToken authentication
    ) {
        Number organizationId = authentication
                .getToken()
                .getClaim("organizationId");

        return organizationId.longValue();
    }

    private long authenticatedUserId(
            JwtAuthenticationToken authentication
    ) {
        return Long.parseLong(
                authentication.getToken().getSubject()
        );
    }
}