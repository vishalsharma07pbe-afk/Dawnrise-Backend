package com.dawnrise.school.school.provisioning;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.util.Map;

@Component
public class HttpIdentityProvisioningClient
        implements IdentityProvisioningClient {

    private static final String PROVISIONING_PATH =
            "/internal/v1/school-provisioning/initial-authority";

    private final RestClient identityProvisioningRestClient;
    private final IdentityProvisioningProperties properties;

    public HttpIdentityProvisioningClient(
            RestClient identityProvisioningRestClient,
            IdentityProvisioningProperties properties
    ) {
        this.identityProvisioningRestClient =
                identityProvisioningRestClient;
        this.properties = properties;
    }

    @Override
    public IdentityProvisioningResponse provisionInitialAuthority(
            IdentityProvisioningRequest request,
            String idempotencyKey
    ) {
        validateConfiguration();

        try {
            IdentityProvisioningResponse response =
                    identityProvisioningRestClient
                            .post()
                            .uri(PROVISIONING_PATH)
                            .header(
                                    "Idempotency-Key",
                                    idempotencyKey
                            )
                            .header(
                                    "X-Service-Name",
                                    properties.getServiceName()
                            )
                            .header(
                                    "X-Internal-Api-Key",
                                    properties.getApiKey()
                            )
                            .body(request)
                            .retrieve()
                            .body(IdentityProvisioningResponse.class);

            if (response == null) {
                throw new IllegalStateException(
                        "Identity-service returned an empty "
                                + "provisioning response"
                );
            }

            return response;
        } catch (RestClientResponseException exception) {
            throw translateError(exception);
        }
    }

    private void validateConfiguration() {
        if (properties.getApiKey() == null
                || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Identity-service internal API key "
                            + "is not configured"
            );
        }

        if (properties.getServiceName() == null
                || properties.getServiceName().isBlank()) {
            throw new IllegalStateException(
                    "Identity-service caller name is not configured"
            );
        }
    }

    private IdentityProvisioningException translateError(
            RestClientResponseException exception
    ) {
        int statusCode = exception
                .getStatusCode()
                .value();

        try {
            IdentityProvisioningErrorResponse response =
                    exception.getResponseBodyAs(
                            IdentityProvisioningErrorResponse.class
                    );

            if (response != null) {
                String safeMessage =
                        response.message() == null
                                || response.message().isBlank()
                                ? "Identity provisioning request was rejected."
                                : response.message();

                return new IdentityProvisioningException(
                        statusCode,
                        safeMessage,
                        response.validationErrors()
                );
            }
        } catch (RuntimeException ignored) {
            // Never expose response parsing or HTTP-client internals.
        }

        return new IdentityProvisioningException(
                statusCode,
                "Identity provisioning request was rejected.",
                Map.of()
        );
    }
}