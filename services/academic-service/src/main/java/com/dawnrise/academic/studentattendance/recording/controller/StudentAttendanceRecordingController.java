package com.dawnrise.academic.studentattendance.recording.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.studentattendance.recording.dto.BulkStudentAttendanceRecordRequest;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceRecordingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/student-attendance")
public class StudentAttendanceRecordingController {

    private final StudentAttendanceRecordingService service;

    public StudentAttendanceRecordingController(
            StudentAttendanceRecordingService service
    ) {
        this.service = service;
    }

    @PostMapping("/academic-years/{academicYearId}/grades/{gradeLevelId}/sections/{sectionId}/dates/{attendanceDate}/draft")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<StudentAttendanceSessionResponse> getOrCreateDraft(
            @PathVariable long academicYearId,
            @PathVariable long gradeLevelId,
            @PathVariable long sectionId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.status(201).body(service.getOrCreateDraft(
                organizationId(authentication),
                userId(authentication),
                academicYearId,
                gradeLevelId,
                sectionId,
                attendanceDate
        ));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<StudentAttendanceSessionResponse> getSession(
            @PathVariable long sessionId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.getSession(
                organizationId(authentication),
                userId(authentication),
                sessionId
        ));
    }

    @PutMapping("/sessions/{sessionId}/records")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<StudentAttendanceSessionResponse> saveDraftRecords(
            @PathVariable long sessionId,
            @Valid @RequestBody BulkStudentAttendanceRecordRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.saveDraftRecords(
                organizationId(authentication),
                userId(authentication),
                sessionId,
                request
        ));
    }

    @PostMapping("/sessions/{sessionId}/submit")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_SUBMIT')
            """)
    public ResponseEntity<StudentAttendanceSessionResponse> submitManually(
            @PathVariable long sessionId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.submitManually(
                organizationId(authentication),
                userId(authentication),
                sessionId
        ));
    }

    @GetMapping("/academic-years/{academicYearId}/grades/{gradeLevelId}/sections/{sectionId}/dates/{attendanceDate}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<StudentAttendanceSessionResponse> getSectionAttendanceForDate(
            @PathVariable long academicYearId,
            @PathVariable long gradeLevelId,
            @PathVariable long sectionId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.getSectionAttendanceForDate(
                organizationId(authentication),
                userId(authentication),
                academicYearId,
                gradeLevelId,
                sectionId,
                attendanceDate
        ));
    }

    private static long organizationId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.organizationId(authentication);
    }

    private static long userId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.userId(authentication);
    }
}
