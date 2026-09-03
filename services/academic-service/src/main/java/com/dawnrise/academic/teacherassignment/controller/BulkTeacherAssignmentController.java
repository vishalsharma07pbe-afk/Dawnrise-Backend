package com.dawnrise.academic.teacherassignment.controller;

import com.dawnrise.academic.teacherassignment.dto.BulkCreateTeacherAssignmentsRequest;
import com.dawnrise.academic.teacherassignment.dto.TeacherAssignmentResponse;
import com.dawnrise.academic.teacherassignment.service.TeacherAssignmentService;
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
@RequestMapping("/api/v1/academic-years/{academicYearId}/teacher-assignments")
@Validated
public class BulkTeacherAssignmentController {

    private final TeacherAssignmentService assignmentService;

    public BulkTeacherAssignmentController(
            TeacherAssignmentService assignmentService
    ) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/bulk")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('TEACHER_ASSIGNMENT_CREATE')
            """)
    public ResponseEntity<List<TeacherAssignmentResponse>> createBulk(
            @PathVariable @Positive long academicYearId,
            @Valid @RequestBody BulkCreateTeacherAssignmentsRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(assignmentService.createBulk(
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
