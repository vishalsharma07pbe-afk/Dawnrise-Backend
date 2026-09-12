package com.dawnrise.academic.studentattendance.correction.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.studentattendance.correction.dto.CreateStudentAttendanceCorrectionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionDecisionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionResponse;
import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;
import com.dawnrise.academic.studentattendance.correction.service.StudentAttendanceCorrectionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student-attendance/corrections")
public class StudentAttendanceCorrectionController {

    private final StudentAttendanceCorrectionService service;

    public StudentAttendanceCorrectionController(StudentAttendanceCorrectionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CORRECTION_REQUEST')
            """)
    public ResponseEntity<StudentAttendanceCorrectionResponse> create(
            @RequestParam long attendanceSessionId,
            @Valid @RequestBody CreateStudentAttendanceCorrectionRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.status(201).body(service.create(
                organizationId(authentication),
                userId(authentication),
                attendanceSessionId,
                request
        ));
    }

    @GetMapping("/{correctionRequestId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CORRECTION_VIEW')
            """)
    public ResponseEntity<StudentAttendanceCorrectionResponse> get(
            @PathVariable long correctionRequestId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.get(
                organizationId(authentication),
                userId(authentication),
                correctionRequestId
        ));
    }

    @GetMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CORRECTION_VIEW')
            """)
    public ResponseEntity<Page<StudentAttendanceCorrectionResponse>> list(
            @RequestParam(required = false) Long attendanceSessionId,
            @RequestParam(required = false) StudentAttendanceCorrectionStatus status,
            Pageable pageable,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.list(
                organizationId(authentication),
                userId(authentication),
                attendanceSessionId,
                status,
                pageable
        ));
    }

    @PostMapping("/{correctionRequestId}/cancel")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CORRECTION_REQUEST')
            """)
    public ResponseEntity<StudentAttendanceCorrectionResponse> cancel(
            @PathVariable long correctionRequestId,
            @Valid @RequestBody StudentAttendanceCorrectionDecisionRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.cancel(
                organizationId(authentication),
                userId(authentication),
                correctionRequestId,
                request
        ));
    }

    @PostMapping("/{correctionRequestId}/reject")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CORRECTION_APPROVE')
            """)
    public ResponseEntity<StudentAttendanceCorrectionResponse> reject(
            @PathVariable long correctionRequestId,
            @Valid @RequestBody StudentAttendanceCorrectionDecisionRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.reject(
                organizationId(authentication),
                userId(authentication),
                correctionRequestId,
                request
        ));
    }

    @PostMapping("/{correctionRequestId}/approve")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_CORRECTION_APPROVE')
            """)
    public ResponseEntity<StudentAttendanceCorrectionResponse> approve(
            @PathVariable long correctionRequestId,
            @Valid @RequestBody StudentAttendanceCorrectionDecisionRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.approve(
                organizationId(authentication),
                userId(authentication),
                correctionRequestId,
                request
        ));
    }

    private static long organizationId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.organizationId(authentication);
    }

    private static long userId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.userId(authentication);
    }
}
