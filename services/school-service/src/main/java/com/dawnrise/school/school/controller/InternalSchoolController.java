package com.dawnrise.school.school.controller;

import com.dawnrise.school.school.DTO.SchoolTimeZoneResponse;
import com.dawnrise.school.school.service.SchoolService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/organizations")
@Validated
public class InternalSchoolController {

    private final SchoolService schoolService;

    public InternalSchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping("/{organizationId}/time-zone")
    @PreAuthorize("""
            hasRole('INTERNAL_SERVICE')
            and authentication.name == 'academic-service'
            """)
    public ResponseEntity<SchoolTimeZoneResponse> getSchoolTimeZone(
            @PathVariable
            @Positive(message = "Organization ID must be positive")
            Long organizationId
    ) {
        return ResponseEntity.ok(
                schoolService.getSchoolTimeZone(organizationId)
        );
    }
}
