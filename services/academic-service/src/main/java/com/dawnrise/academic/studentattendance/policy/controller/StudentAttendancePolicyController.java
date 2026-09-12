package com.dawnrise.academic.studentattendance.policy.controller;

import com.dawnrise.academic.studentattendance.policy.dto.StudentAttendancePolicyRequest;
import com.dawnrise.academic.studentattendance.policy.dto.StudentAttendancePolicyResponse;
import com.dawnrise.academic.studentattendance.policy.service.StudentAttendancePolicyService;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student-attendance/policy")
public class StudentAttendancePolicyController {

    private final StudentAttendancePolicyService service;

    public StudentAttendancePolicyController(
            StudentAttendancePolicyService service
    ) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_POLICY_VIEW')
            """)
    public ResponseEntity<StudentAttendancePolicyResponse> get(
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.get(organizationId(authentication))
        );
    }

    @PostMapping("/initialize")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_POLICY_MANAGE')
            """)
    public ResponseEntity<StudentAttendancePolicyResponse> initialize(
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.status(201)
                .body(service.initialize(
                        organizationId(authentication),
                        authenticatedUserId(authentication)
                ));
    }

    @PutMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_POLICY_MANAGE')
            """)
    public ResponseEntity<StudentAttendancePolicyResponse> update(
            @Valid @RequestBody StudentAttendancePolicyRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.update(
                        organizationId(authentication),
                        authenticatedUserId(authentication),
                        request
                )
        );
    }

    private static long organizationId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.organizationId(authentication);
    }

    private static long authenticatedUserId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.userId(authentication);
    }
}
