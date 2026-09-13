package com.dawnrise.academic.studentattendance.offlinesync.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;
import com.dawnrise.academic.studentattendance.offlinesync.service.StudentAttendanceOfflineSyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student-attendance/offline-sync")
public class StudentAttendanceOfflineSyncController {

    private final StudentAttendanceOfflineSyncService service;

    public StudentAttendanceOfflineSyncController(StudentAttendanceOfflineSyncService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<StudentAttendanceOfflineSyncResponse> synchronize(
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody StudentAttendanceOfflineSyncRequest request,
            JwtAuthenticationToken authentication
    ) {
        StudentAttendanceOfflineSyncResponse response = service.synchronize(
                AuthenticatedAcademicActor.organizationId(authentication),
                AuthenticatedAcademicActor.userId(authentication),
                idempotencyKey,
                request
        );
        return ResponseEntity.status(
                response.replay() ? HttpStatus.OK : HttpStatus.CREATED
        ).body(response);
    }
}
