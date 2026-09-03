package com.dawnrise.identity.user.eligibility.controller;

import com.dawnrise.identity.user.eligibility.dto.TeachingEligibilityResponse;
import com.dawnrise.identity.user.eligibility.service.TeachingEligibilityService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/internal/v1/organizations/{organizationId}"
                + "/users/{userId}/teaching-eligibility"
)
@Validated
public class TeachingEligibilityController {

    private final TeachingEligibilityService eligibilityService;

    public TeachingEligibilityController(
            TeachingEligibilityService eligibilityService
    ) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping
    @PreAuthorize("""
            hasRole('INTERNAL_SERVICE')
            and authentication.name == 'academic-service'
            """)
    public ResponseEntity<TeachingEligibilityResponse> check(
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
}