package com.dawnrise.academic.studentenrollment.integration.identity;

import com.dawnrise.academic.common.integration.identity.IdentityServiceProperties;
import com.dawnrise.academic.common.integration.identity.BatchIdentityEligibilityRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class HttpIdentityStudentEligibilityClient
        implements IdentityStudentEligibilityClient {

    private static final String ELIGIBILITY_PATH =
            "/internal/v1/organizations/{organizationId}"
                    + "/users/{userId}/student-enrollment-eligibility";
    private static final String BATCH_ELIGIBILITY_PATH =
            "/internal/v1/organizations/{organizationId}"
                    + "/users/student-enrollment-eligibility/batch";

    private final RestClient identityServiceRestClient;
    private final IdentityServiceProperties properties;

    public HttpIdentityStudentEligibilityClient(
            RestClient identityServiceRestClient,
            IdentityServiceProperties properties
    ) {
        this.identityServiceRestClient =
                identityServiceRestClient;
        this.properties = properties;
    }

    @Override
    public StudentEnrollmentEligibilityResponse check(
            long organizationId,
            long userId
    ) {
        validateConfiguration();

        try {
            StudentEnrollmentEligibilityResponse response =
                    identityServiceRestClient
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
                            .body(
                                    StudentEnrollmentEligibilityResponse.class
                            );

            if (response == null) {
                throw new IdentityStudentEligibilityException(
                        "Identity-service returned an empty student eligibility response",
                        null
                );
            }

            return response;
        } catch (IdentityStudentEligibilityException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new IdentityStudentEligibilityException(
                    "Student enrollment eligibility could not be verified",
                    exception
            );
        }
    }

    @Override
    public BatchStudentEnrollmentEligibilityResponse checkBatch(
            long organizationId,
            List<Long> userIds
    ) {
        validateConfiguration();

        try {
            BatchStudentEnrollmentEligibilityResponse response =
                    identityServiceRestClient
                            .post()
                            .uri(
                                    BATCH_ELIGIBILITY_PATH,
                                    organizationId
                            )
                            .header(
                                    "X-Service-Name",
                                    properties.getServiceName()
                            )
                            .header(
                                    "X-Internal-Api-Key",
                                    properties.getApiKey()
                            )
                            .body(new BatchIdentityEligibilityRequest(userIds))
                            .retrieve()
                            .body(
                                    BatchStudentEnrollmentEligibilityResponse.class
                            );

            if (response == null || response.results() == null) {
                throw new IdentityStudentEligibilityException(
                        "Identity-service returned an empty student eligibility response",
                        null
                );
            }

            return response;
        } catch (IdentityStudentEligibilityException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new IdentityStudentEligibilityException(
                    "Student enrollment eligibility could not be verified",
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
