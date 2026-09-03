package com.dawnrise.academic.studentenrollment.controller;

import com.dawnrise.academic.studentenrollment.dto.*;
import com.dawnrise.academic.studentenrollment.service.StudentEnrollmentService;
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
@RequestMapping("/api/v1/academic-years/{academicYearId}")
@Validated
public class StudentEnrollmentController {

    private final StudentEnrollmentService enrollmentService;

    public StudentEnrollmentController(
            StudentEnrollmentService enrollmentService
    ) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping(
            "/grade-levels/{gradeLevelId}"
                    + "/sections/{sectionId}/student-enrollments"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_CREATE')
            """)
    public ResponseEntity<StudentEnrollmentResponse> enroll(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @Valid @RequestBody
            CreateStudentEnrollmentRequest request,
            JwtAuthenticationToken authentication
    ) {
        StudentEnrollmentResponse response =
                enrollmentService.enroll(
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

    @PostMapping(
            "/grade-levels/{gradeLevelId}"
                    + "/sections/{sectionId}/student-enrollments/bulk"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_CREATE')
            """)
    public ResponseEntity<List<StudentEnrollmentResponse>> enrollBulk(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            @Valid @RequestBody
            BulkCreateStudentEnrollmentsRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(enrollmentService.enrollBulk(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        request
                ));
    }

    @GetMapping("/student-enrollments/{enrollmentId}")
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_VIEW')
            """)
    public ResponseEntity<StudentEnrollmentResponse> getById(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long enrollmentId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                enrollmentService.getById(
                        organizationId(authentication),
                        academicYearId,
                        enrollmentId
                )
        );
    }

    @GetMapping(
            "/grade-levels/{gradeLevelId}"
                    + "/sections/{sectionId}/student-enrollments"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_VIEW')
            """)
    public ResponseEntity<List<StudentEnrollmentResponse>>
    getSectionStudents(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long gradeLevelId,
            @PathVariable @Positive long sectionId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                enrollmentService.getSectionStudents(
                        organizationId(authentication),
                        academicYearId,
                        gradeLevelId,
                        sectionId
                )
        );
    }

    @GetMapping(
            "/students/{studentUserId}/enrollment-history"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_VIEW')
            """)
    public ResponseEntity<List<StudentEnrollmentResponse>>
    getStudentHistory(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long studentUserId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                enrollmentService.getStudentHistory(
                        organizationId(authentication),
                        studentUserId
                )
        );
    }

    @PutMapping(
            "/student-enrollments/{enrollmentId}/roll-number"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_UPDATE')
            """)
    public ResponseEntity<StudentEnrollmentResponse>
    updateRollNumber(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long enrollmentId,
            @Valid @RequestBody
            UpdateStudentRollNumberRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                enrollmentService.updateRollNumber(
                        organizationId(authentication),
                        academicYearId,
                        enrollmentId,
                        request
                )
        );
    }

    @PostMapping(
            "/student-enrollments/{enrollmentId}/transfer"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_TRANSFER')
            """)
    public ResponseEntity<StudentTransferResponse> transfer(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long enrollmentId,
            @Valid @RequestBody
            TransferStudentEnrollmentRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                enrollmentService.transfer(
                        organizationId(authentication),
                        academicYearId,
                        enrollmentId,
                        request
                )
        );
    }

    @PostMapping(
            "/student-enrollments/{enrollmentId}/withdraw"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_WITHDRAW')
            """)
    public ResponseEntity<StudentEnrollmentResponse> withdraw(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long enrollmentId,
            @Valid @RequestBody
            EndStudentEnrollmentRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                enrollmentService.withdraw(
                        organizationId(authentication),
                        academicYearId,
                        enrollmentId,
                        request
                )
        );
    }

    @PostMapping(
            "/student-enrollments/{enrollmentId}/complete"
    )
    @PreAuthorize("""
            @academicTenantSecurity.hasOrganization(authentication)
            and hasAuthority('STUDENT_ENROLLMENT_COMPLETE')
            """)
    public ResponseEntity<StudentEnrollmentResponse> complete(
            @PathVariable @Positive long academicYearId,
            @PathVariable @Positive long enrollmentId,
            @Valid @RequestBody
            EndStudentEnrollmentRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(
                enrollmentService.complete(
                        organizationId(authentication),
                        academicYearId,
                        enrollmentId,
                        request
                )
        );
    }

    private static long organizationId(
            JwtAuthenticationToken authentication
    ) {
        Number organizationId =
                authentication.getToken().getClaim("organizationId");

        return organizationId.longValue();
    }
}
