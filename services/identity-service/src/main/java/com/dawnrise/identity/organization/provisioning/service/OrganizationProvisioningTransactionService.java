package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationResponse;

public interface OrganizationProvisioningTransactionService {

    ProvisionOrganizationResponse provisionAtomically(
            Long provisioningRequestId,
            ProvisionOrganizationRequest request
    );
}