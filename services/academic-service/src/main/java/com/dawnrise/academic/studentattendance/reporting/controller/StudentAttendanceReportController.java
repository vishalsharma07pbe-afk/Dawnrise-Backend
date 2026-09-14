package com.dawnrise.academic.studentattendance.reporting.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.studentattendance.reporting.dto.*;
import com.dawnrise.academic.studentattendance.reporting.service.StudentAttendanceReportService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/student-attendance/reports")
@Validated
public class StudentAttendanceReportController {

    private final StudentAttendanceReportService service;

    public StudentAttendanceReportController(
            StudentAttendanceReportService service
    ) {
        this.service = service;
    }

    @GetMapping(
            "/academic-years/{academicYearId}"
                    + "/grades/{gradeLevelId}"
                    + "/sections/{sectionId}"
                    + "/daily"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<DailySectionAttendanceReportResponse>
    getDailySectionReport(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate attendanceDate,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getDailySectionReport(
                        organizationId(authentication),
                        userId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        attendanceDate
                )
        );
    }

    @GetMapping(
            "/academic-years/{academicYearId}"
                    + "/grades/{gradeLevelId}"
                    + "/sections/{sectionId}"
                    + "/students/{studentUserId}/history"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<StudentAttendanceHistoryReportResponse>
    getStudentHistory(
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
            Pageable pageable,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getStudentHistory(
                        organizationId(authentication),
                        userId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        studentUserId,
                        fromDate,
                        toDate,
                        pageable
                )
        );
    }

    @GetMapping(
            "/academic-years/{academicYearId}"
                    + "/grades/{gradeLevelId}"
                    + "/sections/{sectionId}"
                    + "/students/{studentUserId}/monthly"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<MonthlyStudentAttendanceReportResponse>
    getStudentMonthlySummary(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @PathVariable @Positive long studentUserId,
            @RequestParam
            @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth month,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getStudentMonthlySummary(
                        organizationId(authentication),
                        userId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        studentUserId,
                        month
                )
        );
    }

    @GetMapping(
            "/academic-years/{academicYearId}"
                    + "/grades/{gradeLevelId}"
                    + "/sections/{sectionId}/summary"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<SectionAttendanceSummaryResponse>
    getSectionSummary(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getSectionSummary(
                        organizationId(authentication),
                        userId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        fromDate,
                        toDate
                )
        );
    }

    @GetMapping(
            "/academic-years/{academicYearId}"
                    + "/grades/{gradeLevelId}/summary"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<GradeAttendanceSummaryResponse>
    getGradeSummary(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getGradeSummary(
                        organizationId(authentication),
                        userId(authentication),
                        academicYearId,
                        gradeLevelId,
                        fromDate,
                        toDate
                )
        );
    }

    @GetMapping(
            "/academic-years/{academicYearId}/low-attendance"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<LowAttendanceReportResponse>
    getLowAttendanceStudents(
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
            Pageable pageable,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getLowAttendanceStudents(
                        organizationId(authentication),
                        userId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        fromDate,
                        toDate,
                        thresholdPercentage,
                        pageable
                )
        );
    }

    @GetMapping(
            "/academic-years/{academicYearId}/missing"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ATTENDANCE_VIEW')
            """)
    public ResponseEntity<MissingAttendanceReportResponse>
    getMissingAttendance(
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
            Pageable pageable,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                service.getMissingAttendance(
                        organizationId(authentication),
                        userId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        fromDate,
                        toDate,
                        pageable
                )
        );
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