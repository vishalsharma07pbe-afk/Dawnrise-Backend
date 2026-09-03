package com.dawnrise.academic.gradelevelsubject.controller;

import com.dawnrise.academic.gradelevelsubject.dto.ApplyGradeLevelSubjectStructureRequest;
import com.dawnrise.academic.gradelevelsubject.dto.GradeLevelSubjectStructureResponse;
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
@RequestMapping("/api/v1/academic-years/{academicYearId}/grade-level-subjects")
@Validated
public class GradeLevelSubjectStructureController {

    private final GradeLevelSubjectService assignmentService;

    public GradeLevelSubjectStructureController(
            GradeLevelSubjectService assignmentService
    ) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/apply-structure")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('GRADE_SUBJECT_ASSIGN')
            """)
    public ResponseEntity<List<GradeLevelSubjectStructureResponse>>
    applyStructure(
            @PathVariable @Positive long academicYearId,
            @Valid @RequestBody ApplyGradeLevelSubjectStructureRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(assignmentService.applyStructure(
                        organizationId(authentication),
                        academicYearId,
                        request
                ));
    }

    private static long organizationId(
            JwtAuthenticationToken authentication
    ) {
        Number organizationId =
                authentication.getToken().getClaim("organizationId");

        return organizationId.longValue();
    }
}
