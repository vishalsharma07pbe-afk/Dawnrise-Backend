package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.organization.provisioning.entity.OrganizationProvisioningRequest;

public interface ProvisioningRequestLifecycleService {

    OrganizationProvisioningRequest prepareRequest(
            String idempotencyKey,
            Long organizationId,
            String requestHash
    );

    void markFailed(
            Long provisioningRequestId,
            String errorSummary
    );
}