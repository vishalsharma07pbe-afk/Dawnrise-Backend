package com.dawnrise.academic.results.integration.identity;

import com.dawnrise.academic.common.integration.identity.IdentityServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;

@Component
public class HttpIdentityGuardianAccessClient implements IdentityGuardianAccessClient {
    private static final String PATH =
            "/internal/v1/organizations/{organizationId}/guardians/"
                    + "{guardianUserId}/students/{studentUserId}/active";
    private static final String RECIPIENT_PATH =
            "/internal/v1/organizations/{organizationId}/students/{studentUserId}/guardians/active";

    private final RestClient client;
    private final IdentityServiceProperties properties;

    public HttpIdentityGuardianAccessClient(
            RestClient identityServiceRestClient,
            IdentityServiceProperties properties
    ) {
        this.client = identityServiceRestClient;
        this.properties = properties;
    }

    @Override
    public boolean hasActiveRelationship(
            long organizationId,
            long guardianUserId,
            long studentUserId
    ) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getServiceName() == null || properties.getServiceName().isBlank()) {
            throw unavailable(null);
        }
        try {
            GuardianStudentAccessResponse response = client.get()
                    .uri(PATH, organizationId, guardianUserId, studentUserId)
                    .header("X-Service-Name", properties.getServiceName())
                    .header("X-Internal-Api-Key", properties.getApiKey())
                    .retrieve()
                    .body(GuardianStudentAccessResponse.class);
            if (response == null) {
                throw new IdentityGuardianAccessException(
                        "Identity-service returned an empty guardian access response",
                        null
                );
            }
            return response.active();
        } catch (IdentityGuardianAccessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<Long> activeGuardianUserIds(long organizationId, long studentUserId) {
        requireConfiguration();
        try {
            ActiveGuardianRecipientsResponse response = client.get()
                    .uri(RECIPIENT_PATH, organizationId, studentUserId)
                    .header("X-Service-Name", properties.getServiceName())
                    .header("X-Internal-Api-Key", properties.getApiKey())
                    .retrieve().body(ActiveGuardianRecipientsResponse.class);
            if (response == null || response.guardianUserIds() == null) {
                throw new IdentityGuardianAccessException(
                        "Identity-service returned an empty guardian recipient response", null);
            }
            return response.guardianUserIds().stream().filter(id -> id != null && id > 0)
                    .distinct().sorted().toList();
        } catch (IdentityGuardianAccessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private void requireConfiguration() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getServiceName() == null || properties.getServiceName().isBlank()) {
            throw unavailable(null);
        }
    }

    private static IdentityGuardianAccessException unavailable(Throwable cause) {
        return new IdentityGuardianAccessException(
                "Guardian relationship verification is unavailable",
                cause
        );
    }
}
