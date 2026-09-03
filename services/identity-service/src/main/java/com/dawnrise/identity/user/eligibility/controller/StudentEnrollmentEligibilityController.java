package com.dawnrise.identity.user.eligibility.controller;

import com.dawnrise.identity.user.eligibility.dto.BatchEligibilityRequest;
import com.dawnrise.identity.user.eligibility.dto.BatchStudentEnrollmentEligibilityResponse;
import com.dawnrise.identity.user.eligibility.dto.StudentEnrollmentEligibilityResponse;
import com.dawnrise.identity.user.eligibility.service.StudentEnrollmentEligibilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/organizations/{organizationId}")
@Validated
public class StudentEnrollmentEligibilityController {

    private final StudentEnrollmentEligibilityService eligibilityService;

    public StudentEnrollmentEligibilityController(
            StudentEnrollmentEligibilityService eligibilityService
    ) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/users/{userId}/student-enrollment-eligibility")
    @PreAuthorize("""
            hasRole('INTERNAL_SERVICE')
            and authentication.name == 'academic-service'
            """)
    public ResponseEntity<StudentEnrollmentEligibilityResponse> check(
            @PathVariable
            @Positive(message = "Organization ID must be positive")
            long organizationId,
            @PathVariable
            @Positive(message = "User ID must be positive")
            long userId
    ) {
        return ResponseEntity.ok(
                eligibilityService.check(
                        organizationId,
                        userId
                )
        );
    }

    @PostMapping("/users/student-enrollment-eligibility/batch")
    @PreAuthorize("""
            hasRole('INTERNAL_SERVICE')
            and authentication.name == 'academic-service'
            """)
    public ResponseEntity<BatchStudentEnrollmentEligibilityResponse> checkBatch(
            @PathVariable
            @Positive(message = "Organization ID must be positive")
            long organizationId,
            @Valid @RequestBody BatchEligibilityRequest request
    ) {
        BatchEligibilityRequestValidator.rejectDuplicateUserIds(request);

        return ResponseEntity.ok(
                new BatchStudentEnrollmentEligibilityResponse(
                        eligibilityService.checkBatch(
                                organizationId,
                                request.userIds()
                        )
                )
        );
    }
}
