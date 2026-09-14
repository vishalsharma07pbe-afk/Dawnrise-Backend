package com.dawnrise.academic.studentattendance.recording.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse;
import com.dawnrise.academic.studentattendance.discovery.service.StudentAttendanceAccessibleSectionService;
import com.dawnrise.academic.studentattendance.recording.dto.BulkStudentAttendanceRecordRequest;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.recording.dto.SubmitStudentAttendanceRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineDraftSnapshot;
import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceRecordingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/student-attendance")
public class StudentAttendanceRecordingController {

    private final StudentAttendanceRecordingService service;
    private final StudentAttendanceAccessibleSectionService
            accessibleSectionService;

    public StudentAttendanceRecordingController(
            StudentAttendanceRecordingService service,
            StudentAttendanceAccessibleSectionService accessibleSectionService
    ) {
        this.service = service;
        this.accessibleSectionService = accessibleSectionService;
    }

    @GetMapping("/accessible-sections")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<List<AccessibleStudentAttendanceSectionResponse>>
    listAccessibleSections(
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(accessibleSectionService.listAccessibleSections(
                organizationId(authentication),
                userId(authentication)
        ));
    }

    @PostMapping("/academic-years/{academicYearId}/grades/{gradeLevelId}/sections/{sectionId}/dates/{attendanceDate}/draft")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<StudentAttendanceSessionResponse> getOrCreateDraft(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
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
            @PathVariable @Positive long sessionId,
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
            @PathVariable @Positive long sessionId,
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
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody SubmitStudentAttendanceRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.submitManually(
                organizationId(authentication),
                userId(authentication),
                sessionId,
                request
        ));
    }

    @GetMapping("/academic-years/{academicYearId}/grades/{gradeLevelId}/sections/{sectionId}/dates/{attendanceDate}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<StudentAttendanceSessionResponse> getSectionAttendanceForDate(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
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

    @GetMapping("/academic-years/{academicYearId}/grades/{gradeLevelId}/sections/{sectionId}/dates/{attendanceDate}/roster")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<StudentAttendanceOfflineDraftSnapshot>
    getDateEligibleRoster(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate attendanceDate,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.previewOfflineDraft(
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
