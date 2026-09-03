package com.dawnrise.academic.teacherassignment.integration.identity;

import com.dawnrise.academic.common.integration.identity.IdentityServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpIdentityTeacherEligibilityClientTest {

    @Test
    void checkSendsExpectedPathAndInternalHeaders() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://identity-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpIdentityTeacherEligibilityClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://identity-service/internal/v1/organizations/10"
                                + "/users/20/teaching-eligibility"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "X-Service-Name",
                        "academic-service"
                ))
                .andExpect(header(
                        "X-Internal-Api-Key",
                        "test-key"
                ))
                .andRespond(withSuccess("""
                        {
                          "userId": 20,
                          "organizationId": 10,
                          "displayName": "Anita Sharma",
                          "eligible": true,
                          "reason": "ELIGIBLE"
                        }
                        """, MediaType.APPLICATION_JSON));

        TeachingEligibilityResponse response = client.check(10L, 20L);

        assertTrue(response.eligible());
        assertEquals(TeachingEligibilityReason.ELIGIBLE, response.reason());
        assertEquals("Anita Sharma", response.displayName());
        server.verify();
    }

    @Test
    void checkDeserializesIneligibleResponse() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://identity-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpIdentityTeacherEligibilityClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://identity-service/internal/v1/organizations/10"
                                + "/users/20/teaching-eligibility"
                ))
                .andRespond(withSuccess("""
                        {
                          "userId": 20,
                          "organizationId": 10,
                          "displayName": "Anita Sharma",
                          "eligible": false,
                          "reason": "TEACHER_ROLE_REQUIRED"
                        }
                        """, MediaType.APPLICATION_JSON));

        TeachingEligibilityResponse response = client.check(10L, 20L);

        assertFalse(response.eligible());
        assertEquals(
                TeachingEligibilityReason.TEACHER_ROLE_REQUIRED,
                response.reason()
        );
        server.verify();
    }

    @Test
    void checkBatchSendsExpectedPathHeadersAndBody() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://identity-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpIdentityTeacherEligibilityClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://identity-service/internal/v1/organizations/10"
                                + "/users/teaching-eligibility/batch"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "X-Service-Name",
                        "academic-service"
                ))
                .andExpect(header(
                        "X-Internal-Api-Key",
                        "test-key"
                ))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "userId": 20,
                              "organizationId": 10,
                              "displayName": "Anita Sharma",
                              "eligible": true,
                              "reason": "ELIGIBLE"
                            },
                            {
                              "userId": 21,
                              "organizationId": 10,
                              "displayName": null,
                              "eligible": false,
                              "reason": "USER_NOT_FOUND"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        BatchTeachingEligibilityResponse response =
                client.checkBatch(10L, List.of(20L, 21L));

        assertEquals(2, response.results().size());
        assertTrue(response.results().getFirst().eligible());
        assertFalse(response.results().get(1).eligible());
        server.verify();
    }

    @Test
    void checkBatchRejectsEmptyResponse() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://identity-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpIdentityTeacherEligibilityClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://identity-service/internal/v1/organizations/10"
                                + "/users/teaching-eligibility/batch"
                ))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        IdentityTeacherEligibilityException exception = assertThrows(
                IdentityTeacherEligibilityException.class,
                () -> client.checkBatch(10L, List.of(20L))
        );

        assertEquals(
                "Identity-service returned an empty eligibility response",
                exception.getMessage()
        );
        server.verify();
    }

    @Test
    void checkRejectsEmptyResponse() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://identity-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpIdentityTeacherEligibilityClient client =
                client(builder.build(), "test-key", "academic-service");

        server.expect(requestTo(
                        "http://identity-service/internal/v1/organizations/10"
                                + "/users/20/teaching-eligibility"
                ))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        IdentityTeacherEligibilityException exception = assertThrows(
                IdentityTeacherEligibilityException.class,
                () -> client.check(10L, 20L)
        );

        assertEquals(
                "Identity-service returned an empty eligibility response",
                exception.getMessage()
        );
        server.verify();
    }

    @Test
    void checkRejectsMissingConfiguration() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://identity-service")
                .build();

        IllegalStateException missingApiKey = assertThrows(
                IllegalStateException.class,
                () -> client(restClient, null, "academic-service")
                        .check(10L, 20L)
        );
        assertEquals(
                "Identity-service internal API key is not configured",
                missingApiKey.getMessage()
        );

        IllegalStateException missingServiceName = assertThrows(
                IllegalStateException.class,
                () -> client(restClient, "test-key", " ")
                        .check(10L, 20L)
        );
        assertEquals(
                "Identity-service caller name is not configured",
                missingServiceName.getMessage()
        );
    }

    @Test
    void identityErrorResponsesBecomeEligibilityException() {
        for (HttpStatus status : new HttpStatus[]{
                HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND,
                HttpStatus.INTERNAL_SERVER_ERROR
        }) {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl("http://identity-service");
            MockRestServiceServer server =
                    MockRestServiceServer.bindTo(builder).build();
            HttpIdentityTeacherEligibilityClient client =
                    client(builder.build(), "test-key", "academic-service");

            server.expect(requestTo(
                            "http://identity-service/internal/v1/organizations/10"
                                    + "/users/20/teaching-eligibility"
                    ))
                    .andRespond(withStatus(status)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("""
                                    {
                                      "message": "identity internal failure",
                                      "secret": "test-key"
                                    }
                                    """));

            IdentityTeacherEligibilityException exception = assertThrows(
                    IdentityTeacherEligibilityException.class,
                    () -> client.check(10L, 20L)
            );

            assertEquals(
                    "Teacher eligibility could not be verified",
                    exception.getMessage()
            );
            server.verify();
        }
    }

    @Test
    void transportFailureBecomesEligibilityException() {
        RestClient restClient = RestClient.builder()
                .requestFactory((uri, httpMethod) -> {
                    throw new IOException("connection refused");
                })
                .build();
        HttpIdentityTeacherEligibilityClient client =
                client(restClient, "test-key", "academic-service");

        IdentityTeacherEligibilityException exception = assertThrows(
                IdentityTeacherEligibilityException.class,
                () -> client.check(10L, 20L)
        );

        assertEquals(
                "Teacher eligibility could not be verified",
                exception.getMessage()
        );
    }

    private static HttpIdentityTeacherEligibilityClient client(
            RestClient restClient,
            String apiKey,
            String serviceName
    ) {
        IdentityServiceProperties properties =
                new IdentityServiceProperties();
        properties.setApiKey(apiKey);
        properties.setServiceName(serviceName);
        return new HttpIdentityTeacherEligibilityClient(
                restClient,
                properties
        );
    }
}
