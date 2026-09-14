package com.dawnrise.academic.studentattendance.reporting.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.studentattendance.reporting.export.StudentAttendanceReportExport;
import com.dawnrise.academic.studentattendance.reporting.export.StudentAttendanceReportExportFormat;
import com.dawnrise.academic.studentattendance.reporting.export.StudentAttendanceReportExportService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/student-attendance/reports")
@Validated
public class StudentAttendanceReportExportController {

    private final StudentAttendanceReportExportService service;

    public StudentAttendanceReportExportController(
            StudentAttendanceReportExportService service
    ) {
        this.service = service;
    }

    @GetMapping(
            "/academic-years/{academicYearId}"
                    + "/grades/{gradeLevelId}"
                    + "/sections/{sectionId}"
                    + "/daily/export"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<byte[]> exportDailySection(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate attendanceDate,
            @RequestParam StudentAttendanceReportExportFormat format,
            JwtAuthenticationToken authentication
    ) {
        return exportResponse(service.exportDailySection(
                organizationId(authentication),
                userId(authentication),
                academicYearId,
                gradeLevelId,
                sectionId,
                attendanceDate,
                format
        ));
    }

    @GetMapping(
            "/academic-years/{academicYearId}"
                    + "/grades/{gradeLevelId}"
                    + "/sections/{sectionId}"
                    + "/students/{studentUserId}/history/export"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<byte[]> exportStudentHistory(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @PathVariable @Positive long studentUserId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam StudentAttendanceReportExportFormat format,
            JwtAuthenticationToken authentication
    ) {
        return exportResponse(service.exportStudentHistory(
                organizationId(authentication),
                userId(authentication),
                academicYearId,
                gradeLevelId,
                sectionId,
                studentUserId,
                fromDate,
                toDate,
                format
        ));
    }

    @GetMapping(
            "/academic-years/{academicYearId}"
                    + "/grades/{gradeLevelId}"
                    + "/sections/{sectionId}/summary/export"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<byte[]> exportSectionSummary(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam StudentAttendanceReportExportFormat format,
            JwtAuthenticationToken authentication
    ) {
        return exportResponse(service.exportSectionSummary(
                organizationId(authentication),
                userId(authentication),
                academicYearId,
                gradeLevelId,
                sectionId,
                fromDate,
                toDate,
                format
        ));
    }

    @GetMapping(
            "/academic-years/{academicYearId}/low-attendance/export"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<byte[]> exportLowAttendance(
            @PathVariable @Positive long academicYearId,
            @RequestParam(required = false)
            @Positive Long gradeLevelId,
            @RequestParam(required = false)
            @Positive Long sectionId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam
            @DecimalMin("0.00")
            @DecimalMax("100.00")
            BigDecimal thresholdPercentage,
            @RequestParam StudentAttendanceReportExportFormat format,
            JwtAuthenticationToken authentication
    ) {
        return exportResponse(service.exportLowAttendance(
                organizationId(authentication),
                userId(authentication),
                academicYearId,
                gradeLevelId,
                sectionId,
                fromDate,
                toDate,
                thresholdPercentage,
                format
        ));
    }

    private static ResponseEntity<byte[]> exportResponse(
            StudentAttendanceReportExport export
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        export.contentType()
                ))
                .contentLength(export.content().length)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(export.filename())
                                .build()
                                .toString()
                )
                .body(export.content());
    }

    private static long organizationId(
            JwtAuthenticationToken authentication
    ) {
        return AuthenticatedAcademicActor
                .organizationId(authentication);
    }

    private static long userId(
            JwtAuthenticationToken authentication
    ) {
        return AuthenticatedAcademicActor
                .userId(authentication);
    }
}
