package com.dawnrise.academic.studentattendance.importing.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.studentattendance.importing.dto.ConfirmStudentAttendanceImportRequest;
import com.dawnrise.academic.studentattendance.importing.dto.StudentAttendanceImportPreviewResponse;
import com.dawnrise.academic.studentattendance.importing.service.StudentAttendanceImportService;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/student-attendance/imports")
public class StudentAttendanceImportController {

    private final StudentAttendanceImportService service;

    public StudentAttendanceImportController(StudentAttendanceImportService service) {
        this.service = service;
    }

    @GetMapping(value = "/template.csv", produces = "text/csv")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<byte[]> csvTemplate() {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=student-attendance-import-template.csv"
                )
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(service.csvTemplate());
    }

    @PostMapping(
            value = "/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<StudentAttendanceImportPreviewResponse> preview(
            @RequestPart("file") MultipartFile file,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.preview(
                organizationId(authentication),
                userId(authentication),
                file
        ));
    }

    @PostMapping("/{previewId}/confirm")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_RECORD')
            """)
    public ResponseEntity<StudentAttendanceOfflineSyncResponse> confirm(
            @PathVariable long previewId,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody ConfirmStudentAttendanceImportRequest request,
            JwtAuthenticationToken authentication
    ) {
        StudentAttendanceOfflineSyncResponse response = service.confirm(
                organizationId(authentication),
                userId(authentication),
                previewId,
                idempotencyKey,
                request
        );
        return ResponseEntity.status(
                response.replay() ? HttpStatus.OK : HttpStatus.CREATED
        ).body(response);
    }

    private static long organizationId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.organizationId(authentication);
    }

    private static long userId(JwtAuthenticationToken authentication) {
        return AuthenticatedAcademicActor.userId(authentication);
    }
}
