package com.dawnrise.academic.notificationdelivery;

import com.dawnrise.academic.common.integration.identity.IdentityServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class HttpIdentityNotificationClient implements IdentityNotificationClient {
    private final RestClient client;
    private final IdentityServiceProperties properties;

    public HttpIdentityNotificationClient(RestClient identityServiceRestClient,
                                          IdentityServiceProperties properties) {
        this.client = identityServiceRestClient;
        this.properties = properties;
    }

    @Override
    public void ingest(NotificationRequest request) {
        requireConfiguration();
        try {
            client.post().uri("/internal/v1/notification-events")
                    .header("X-Service-Name", properties.getServiceName())
                    .header("X-Internal-Api-Key", properties.getApiKey())
                    .body(request).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new NotificationDeliveryException("Identity notification ingestion failed", exception);
        }
    }

    @Override
    public void resolveGroup(long organizationId, String groupingKey, String excludeSourceEventId) {
        requireConfiguration();
        try {
            client.post().uri("/internal/v1/notification-events/resolve-group")
                    .header("X-Service-Name", properties.getServiceName())
                    .header("X-Internal-Api-Key", properties.getApiKey())
                    .body(Map.of("organizationId", organizationId, "groupingKey", groupingKey,
                            "excludeSourceEventId", excludeSourceEventId))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new NotificationDeliveryException("Identity notification resolution failed", exception);
        }
    }

    private void requireConfiguration() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getServiceName() == null || properties.getServiceName().isBlank()) {
            throw new NotificationDeliveryException("Identity notification delivery is not configured", null);
        }
    }
}
