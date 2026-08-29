package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.organization.provisioning.entity.OrganizationProvisioningRequest;
import com.dawnrise.identity.organization.provisioning.enums.ProvisioningRequestStatus;
import com.dawnrise.identity.organization.provisioning.exception.ProvisioningConflictException;
import com.dawnrise.identity.organization.provisioning.exception.ProvisioningInProgressException;
import com.dawnrise.identity.organization.provisioning.repository.OrganizationProvisioningRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProvisioningRequestLifecycleServiceImpl
        implements ProvisioningRequestLifecycleService {

    private final OrganizationProvisioningRequestRepository repository;

    public ProvisioningRequestLifecycleServiceImpl(
            OrganizationProvisioningRequestRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrganizationProvisioningRequest prepareRequest(
            String idempotencyKey,
            Long organizationId,
            String requestHash
    ) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> handleExistingRequest(
                        existing,
                        organizationId,
                        requestHash
                ))
                .orElseGet(() -> createRequest(
                        idempotencyKey,
                        organizationId,
                        requestHash
                ));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            Long provisioningRequestId,
            String errorSummary
    ) {
        OrganizationProvisioningRequest request = repository
                .findById(provisioningRequestId)
                .orElseThrow(() -> new IllegalStateException(
                        "Provisioning request not found: "
                                + provisioningRequestId
                ));

        if (request.getStatus()
                != ProvisioningRequestStatus.PROCESSING) {
            return;
        }

        request.markFailed(sanitizeError(errorSummary));
    }

    private OrganizationProvisioningRequest handleExistingRequest(
            OrganizationProvisioningRequest existing,
            Long organizationId,
            String requestHash
    ) {
        if (!existing.getOrganizationId().equals(organizationId)
                || !existing.getRequestHash().equals(requestHash)) {
            throw new ProvisioningConflictException(
                    "The idempotency key was already used "
                            + "for a different provisioning request"
            );
        }

        if (existing.getStatus()
                == ProvisioningRequestStatus.PROCESSING) {
            throw new ProvisioningInProgressException(
                    "The provisioning request is already being processed"
            );
        }

        if (existing.getStatus()
                == ProvisioningRequestStatus.FAILED) {
            existing.restart();
        }

        // A SUCCEEDED request is returned unchanged so the caller can
        // deserialize and return its previously stored response.
        return existing;
    }

    private OrganizationProvisioningRequest createRequest(
            String idempotencyKey,
            Long organizationId,
            String requestHash
    ) {
        OrganizationProvisioningRequest request =
                new OrganizationProvisioningRequest(
                        idempotencyKey,
                        organizationId,
                        requestHash
                );

        return repository.saveAndFlush(request);
    }

    private String sanitizeError(String errorSummary) {
        String safeMessage =
                errorSummary == null || errorSummary.isBlank()
                        ? "Provisioning failed"
                        : errorSummary.trim();

        return safeMessage.length() <= 500
                ? safeMessage
                : safeMessage.substring(0, 500);
    }
}