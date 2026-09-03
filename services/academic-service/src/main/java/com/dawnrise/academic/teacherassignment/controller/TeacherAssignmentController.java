package com.dawnrise.academic.teacherassignment.controller;

import com.dawnrise.academic.teacherassignment.dto.CreateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.dto.TeacherAssignmentResponse;
import com.dawnrise.academic.teacherassignment.dto.UpdateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.service.TeacherAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/academic-years/{academicYearId}"
                + "/grade-levels/{gradeLevelId}"
                + "/sections/{sectionId}/teacher-assignments"
)
@Validated
public class TeacherAssignmentController {

    private final TeacherAssignmentService assignmentService;

    public TeacherAssignmentController(
            TeacherAssignmentService assignmentService
    ) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('TEACHER_ASSIGNMENT_CREATE')
            """)
    public ResponseEntity<TeacherAssignmentResponse> create(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @Valid @RequestBody CreateTeacherAssignmentRequest request,
            JwtAuthenticationToken authentication
    ) {
        TeacherAssignmentResponse response =
                assignmentService.create(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('TEACHER_ASSIGNMENT_VIEW')
            """)
    public ResponseEntity<List<TeacherAssignmentResponse>> getAll(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                assignmentService.getAll(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId
                )
        );
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('TEACHER_ASSIGNMENT_VIEW')
            """)
    public ResponseEntity<TeacherAssignmentResponse> getById(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @PathVariable @Positive long assignmentId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                assignmentService.getById(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        assignmentId
                )
        );
    }

    @PutMapping("/{assignmentId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('TEACHER_ASSIGNMENT_UPDATE')
            """)
    public ResponseEntity<TeacherAssignmentResponse> update(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @PathVariable @Positive long assignmentId,
            @Valid @RequestBody UpdateTeacherAssignmentRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                assignmentService.update(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        assignmentId,
                        request
                )
        );
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('TEACHER_ASSIGNMENT_REMOVE')
            """)
    public ResponseEntity<Void> remove(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @PathVariable @Positive long assignmentId,
            JwtAuthenticationToken authentication
    ) {
        assignmentService.remove(
                organizationId(authentication),
                academicYearId,
                gradeLevelId,
                sectionId,
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