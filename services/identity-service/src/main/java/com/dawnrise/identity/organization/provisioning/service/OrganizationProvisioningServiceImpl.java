package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationResponse;
import com.dawnrise.identity.organization.provisioning.entity.OrganizationProvisioningRequest;
import com.dawnrise.identity.organization.provisioning.enums.ProvisioningRequestStatus;
import com.dawnrise.identity.organization.provisioning.security.ProvisioningRequestHasher;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrganizationProvisioningServiceImpl
        implements OrganizationProvisioningService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

    private final ProvisioningRequestHasher requestHasher;
    private final ProvisioningRequestLifecycleService lifecycleService;
    private final OrganizationProvisioningTransactionService
            transactionService;
    private final ObjectMapper objectMapper;

    public OrganizationProvisioningServiceImpl(
            ProvisioningRequestHasher requestHasher,
            ProvisioningRequestLifecycleService lifecycleService,
            OrganizationProvisioningTransactionService
                    transactionService,
            ObjectMapper objectMapper
    ) {
        this.requestHasher = requestHasher;
        this.lifecycleService = lifecycleService;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProvisionOrganizationResponse provision(
            String idempotencyKey,
            ProvisionOrganizationRequest request
    ) {
        String validatedKey =
                validateIdempotencyKey(idempotencyKey);

        String requestHash =
                requestHasher.hash(request);

        OrganizationProvisioningRequest provisioningRequest =
                lifecycleService.prepareRequest(
                        validatedKey,
                        request.getOrganizationId(),
                        requestHash
                );

        if (provisioningRequest.getStatus()
                == ProvisioningRequestStatus.SUCCEEDED) {
            return deserializeResponse(
                    provisioningRequest.getResponsePayload()
            );
        }

        try {
            return transactionService.provisionAtomically(
                    provisioningRequest.getId(),
                    request
            );
        } catch (RuntimeException originalException) {
            recordFailure(
                    provisioningRequest.getId(),
                    originalException
            );

            throw originalException;
        }
    }

    private String validateIdempotencyKey(
            String idempotencyKey
    ) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key header is required"
            );
        }

        String normalizedKey = idempotencyKey.trim();

        if (normalizedKey.length()
                > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key cannot exceed "
                            + MAX_IDEMPOTENCY_KEY_LENGTH
                            + " characters"
            );
        }

        return normalizedKey;
    }

    private ProvisionOrganizationResponse deserializeResponse(
            String responsePayload
    ) {
        if (responsePayload == null
                || responsePayload.isBlank()) {
            throw new IllegalStateException(
                    "Successful provisioning request "
                            + "does not contain a response"
            );
        }

        try {
            return objectMapper.readValue(
                    responsePayload,
                    ProvisionOrganizationResponse.class
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not deserialize provisioning response",
                    exception
            );
        }
    }

    private void recordFailure(
            Long provisioningRequestId,
            RuntimeException originalException
    ) {
        try {
            lifecycleService.markFailed(
                    provisioningRequestId,
                    safeErrorMessage(originalException)
            );
        } catch (RuntimeException failureRecordingException) {
            /*
             * Preserve the original provisioning error while retaining
             * information about the secondary audit-recording failure.
             */
            originalException.addSuppressed(
                    failureRecordingException
            );
        }
    }

    private String safeErrorMessage(
            RuntimeException exception
    ) {
        String message = exception.getMessage();

        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}