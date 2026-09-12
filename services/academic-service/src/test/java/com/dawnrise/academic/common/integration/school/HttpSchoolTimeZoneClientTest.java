package com.dawnrise.academic.common.integration.school;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpSchoolTimeZoneClientTest {

    @Test
    void sendsExpectedPathHeadersAndDeserializesZone() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://school-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpSchoolTimeZoneClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://school-service/internal/v1/organizations/10/time-zone"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Name", "academic-service"))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "organizationId": 10,
                          "timeZoneId": "Asia/Kolkata"
                        }
                        """, MediaType.APPLICATION_JSON));

        ZoneId zone = client.getTimeZone(10L);

        assertThat(zone).isEqualTo(ZoneId.of("Asia/Kolkata"));
        server.verify();
    }

    @Test
    void missingConfigurationFailsClosedWithSafeMessage() {
        HttpSchoolTimeZoneClient client =
                client(RestClient.builder().baseUrl("http://school").build(), " ", "academic-service");

        assertSafeFailure(() -> client.getTimeZone(10L));
    }

    @Test
    void emptyResponseFailsClosedWithSafeMessage() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://school-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpSchoolTimeZoneClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://school-service/internal/v1/organizations/10/time-zone"
                ))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertSafeFailure(() -> client.getTimeZone(10L));
        server.verify();
    }

    @Test
    void organizationMismatchFailsClosed() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://school-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpSchoolTimeZoneClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://school-service/internal/v1/organizations/10/time-zone"
                ))
                .andRespond(withSuccess("""
                        {
                          "organizationId": 11,
                          "timeZoneId": "Asia/Kolkata"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertSafeFailure(() -> client.getTimeZone(10L));
        server.verify();
    }

    @Test
    void invalidZoneIdFailsClosed() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://school-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpSchoolTimeZoneClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://school-service/internal/v1/organizations/10/time-zone"
                ))
                .andRespond(withSuccess("""
                        {
                          "organizationId": 10,
                          "timeZoneId": "Not/A_Zone"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertSafeFailure(() -> client.getTimeZone(10L));
        server.verify();
    }

    @Test
    void httpErrorsFailClosedWithSafeMessage() {
        for (HttpStatus status : new HttpStatus[]{
                HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND,
                HttpStatus.INTERNAL_SERVER_ERROR
        }) {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl("http://school-service");
            MockRestServiceServer server =
                    MockRestServiceServer.bindTo(builder).build();
            HttpSchoolTimeZoneClient client =
                    client(builder.build(), "test-key", "academic-service");

            server.expect(requestTo(
                            "http://school-service/internal/v1/organizations/10/time-zone"
                    ))
                    .andRespond(withStatus(status)
                            .contentType(MediaType.TEXT_PLAIN)
                            .body("secret internal response"));

            assertSafeFailure(() -> client.getTimeZone(10L));
            server.verify();
        }
    }

    @Test
    void transportFailureFailsClosedWithSafeMessage() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://school-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpSchoolTimeZoneClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://school-service/internal/v1/organizations/10/time-zone"
                ))
                .andRespond(withException(new IOException("socket secret")));

        assertSafeFailure(() -> client.getTimeZone(10L));
        server.verify();
    }

    private static HttpSchoolTimeZoneClient client(
            RestClient restClient,
            String apiKey,
            String serviceName
    ) {
        SchoolServiceProperties properties = new SchoolServiceProperties();
        properties.setBaseUrl("http://school-service");
        properties.setApiKey(apiKey);
        properties.setServiceName(serviceName);
        return new HttpSchoolTimeZoneClient(restClient, properties);
    }

    private static void assertSafeFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(SchoolTimeZoneUnavailableException.class)
                .hasMessage("School timezone could not be verified")
                .hasMessageNotContaining("test-key")
                .hasMessageNotContaining("school-service")
                .hasMessageNotContaining("secret");
    }
}
