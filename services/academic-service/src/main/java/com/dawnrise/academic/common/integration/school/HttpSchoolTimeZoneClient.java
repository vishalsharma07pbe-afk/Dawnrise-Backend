package com.dawnrise.academic.common.integration.school;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.DateTimeException;
import java.time.ZoneId;

@Component
public class HttpSchoolTimeZoneClient implements SchoolTimeZoneClient {

    private static final String TIME_ZONE_PATH =
            "/internal/v1/organizations/{organizationId}/time-zone";
    private static final String SAFE_FAILURE_MESSAGE =
            "School timezone could not be verified";

    private final RestClient schoolServiceRestClient;
    private final SchoolServiceProperties properties;

    public HttpSchoolTimeZoneClient(
            RestClient schoolServiceRestClient,
            SchoolServiceProperties properties
    ) {
        this.schoolServiceRestClient = schoolServiceRestClient;
        this.properties = properties;
    }

    @Override
    public ZoneId getTimeZone(long organizationId) {
        validateConfiguration();

        try {
            OrganizationTimeZoneResponse response =
                    schoolServiceRestClient
                            .get()
                            .uri(TIME_ZONE_PATH, organizationId)
                            .header("X-Service-Name", properties.getServiceName())
                            .header("X-Internal-Api-Key", properties.getApiKey())
                            .retrieve()
                            .body(OrganizationTimeZoneResponse.class);

            return validateResponse(organizationId, response);
        } catch (SchoolTimeZoneUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new SchoolTimeZoneUnavailableException(
                    SAFE_FAILURE_MESSAGE,
                    exception
            );
        }
    }

    private ZoneId validateResponse(
            long organizationId,
            OrganizationTimeZoneResponse response
    ) {
        if (response == null
                || response.organizationId() == null
                || response.organizationId().longValue() != organizationId
                || response.timeZoneId() == null
                || response.timeZoneId().isBlank()) {
            throw new SchoolTimeZoneUnavailableException(SAFE_FAILURE_MESSAGE);
        }

        try {
            return ZoneId.of(response.timeZoneId());
        } catch (DateTimeException exception) {
            throw new SchoolTimeZoneUnavailableException(
                    SAFE_FAILURE_MESSAGE,
                    exception
            );
        }
    }

    private void validateConfiguration() {
        if (properties.getApiKey() == null
                || properties.getApiKey().isBlank()
                || properties.getServiceName() == null
                || properties.getServiceName().isBlank()
                || properties.getBaseUrl() == null
                || properties.getBaseUrl().isBlank()) {
            throw new SchoolTimeZoneUnavailableException(
                    SAFE_FAILURE_MESSAGE
            );
        }
    }
}
