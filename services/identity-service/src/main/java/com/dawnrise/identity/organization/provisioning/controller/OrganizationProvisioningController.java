package com.dawnrise.identity.organization.provisioning.controller;

import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationResponse;
import com.dawnrise.identity.organization.provisioning.service.OrganizationProvisioningService;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/school-provisioning")
public class OrganizationProvisioningController {

    private final OrganizationProvisioningService provisioningService;

    public OrganizationProvisioningController(
            OrganizationProvisioningService provisioningService
    ) {
        this.provisioningService = provisioningService;
    }

    @PostMapping("/initial-authority")
    @PreAuthorize("""
        hasRole('INTERNAL_SERVICE')
        and authentication.name == 'school-service'
        """)
    public ResponseEntity<ProvisionOrganizationResponse>
    provisionInitialAuthority(
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid @RequestBody
            ProvisionOrganizationRequest request
    ) {
        ProvisionOrganizationResponse response =
                provisioningService.provision(
                        idempotencyKey,
                        request
                );

        return ResponseEntity.ok(response);
    }
}