package com.edusphere.school.school.provisioning;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpIdentityProvisioningClientTest {

    @Test
    void provisionInitialAuthority_whenIdentityReturnsStructuredError_parsesSafely() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://identity-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpIdentityProvisioningClient client = client(builder.build());

        server.expect(requestTo(
                        "http://identity-service/internal/v1/school-provisioning/initial-authority"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "message": "Provisioning request conflicts with existing data",
                                  "validationErrors": {
                                    "authority.email": "This email address is already registered."
                                  }
                                }
                                """));

        IdentityProvisioningException exception = assertThrows(
                IdentityProvisioningException.class,
                () -> client.provisionInitialAuthority(
                        request(),
                        "key-1"
                )
        );

        assertEquals(409, exception.getStatusCode());
        assertEquals(
                "Provisioning request conflicts with existing data",
                exception.getMessage()
        );
        assertEquals(
                "This email address is already registered.",
                exception.getFieldErrors().get("authority.email")
        );
        server.verify();
    }

    @Test
    void provisionInitialAuthority_whenIdentityErrorBodyMalformed_usesGenericSafeMessage() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://identity-service");
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        HttpIdentityProvisioningClient client = client(builder.build());

        server.expect(requestTo(
                        "http://identity-service/internal/v1/school-provisioning/initial-authority"
                ))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{not-json"));

        IdentityProvisioningException exception = assertThrows(
                IdentityProvisioningException.class,
                () -> client.provisionInitialAuthority(
                        request(),
                        "key-2"
                )
        );

        assertEquals(500, exception.getStatusCode());
        assertEquals(
                "Identity provisioning request was rejected.",
                exception.getMessage()
        );
        assertEquals(0, exception.getFieldErrors().size());
        server.verify();
    }

    private HttpIdentityProvisioningClient client(
            RestClient restClient
    ) {
        IdentityProvisioningProperties properties =
                new IdentityProvisioningProperties();
        properties.setApiKey("test-key");
        properties.setServiceName("school-service");
        return new HttpIdentityProvisioningClient(
                restClient,
                properties
        );
    }

    private IdentityProvisioningRequest request() {
        return new IdentityProvisioningRequest(
                1L,
                "SCH001",
                "EduSphere Public School",
                "school@edusphere.com",
                new IdentityInitialAuthorityRequest(
                        "authority.one",
                        "Authority",
                        null,
                        "One",
                        "authority@edusphere.com",
                        "9876543211"
                )
        );
    }
}
