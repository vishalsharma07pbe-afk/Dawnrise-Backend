package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationResponse;

public interface OrganizationProvisioningService {

    ProvisionOrganizationResponse provision(
            String idempotencyKey,
            ProvisionOrganizationRequest request
    );
}