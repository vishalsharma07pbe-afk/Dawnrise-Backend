package com.dawnrise.academic.teacherassignment.integration.identity;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpIdentityTeacherEligibilityClient
        implements IdentityTeacherEligibilityClient {

    private static final String ELIGIBILITY_PATH =
            "/internal/v1/organizations/{organizationId}"
                    + "/users/{userId}/teaching-eligibility";

    private final RestClient identityTeacherEligibilityRestClient;
    private final IdentityTeacherEligibilityProperties properties;

    public HttpIdentityTeacherEligibilityClient(
            RestClient identityTeacherEligibilityRestClient,
            IdentityTeacherEligibilityProperties properties
    ) {
        this.identityTeacherEligibilityRestClient =
                identityTeacherEligibilityRestClient;
        this.properties = properties;
    }

    @Override
    public TeachingEligibilityResponse check(
            long organizationId,
            long userId
    ) {
        validateConfiguration();

        try {
            TeachingEligibilityResponse response =
                    identityTeacherEligibilityRestClient
                            .get()
                            .uri(
                                    ELIGIBILITY_PATH,
                                    organizationId,
                                    userId
                            )
                            .header(
                                    "X-Service-Name",
                                    properties.getServiceName()
                            )
                            .header(
                                    "X-Internal-Api-Key",
                                    properties.getApiKey()
                            )
                            .retrieve()
                            .body(TeachingEligibilityResponse.class);

            if (response == null) {
                throw new IdentityTeacherEligibilityException(
                        "Identity-service returned an empty eligibility response",
                        null
                );
            }

            return response;
        } catch (IdentityTeacherEligibilityException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new IdentityTeacherEligibilityException(
                    "Teacher eligibility could not be verified",
                    exception
            );
        }
    }

    private void validateConfiguration() {
        if (properties.getApiKey() == null
                || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Identity-service internal API key is not configured"
            );
        }

        if (properties.getServiceName() == null
                || properties.getServiceName().isBlank()) {
            throw new IllegalStateException(
                    "Identity-service caller name is not configured"
            );
        }
    }
}