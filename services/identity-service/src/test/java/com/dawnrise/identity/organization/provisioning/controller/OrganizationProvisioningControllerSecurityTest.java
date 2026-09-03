package com.dawnrise.identity.organization.provisioning.controller;

import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import com.dawnrise.identity.organization.enums.OrganizationStatus;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationResponse;
import com.dawnrise.identity.organization.provisioning.enums.ProvisioningRequestStatus;
import com.dawnrise.identity.organization.provisioning.service.OrganizationProvisioningService;
import com.dawnrise.identity.user.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = OrganizationProvisioningController.class,
        properties = {
                "security.internal.api-key=test-key",
                "security.internal.allowed-service-names[0]=school-service",
                "security.internal.allowed-service-names[1]=academic-service"
        }
)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        OrganizationProvisioningControllerSecurityTest.TestConfig.class
})
class OrganizationProvisioningControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void schoolServiceRemainsAllowed() throws Exception {
        mockMvc.perform(post("/internal/v1/school-provisioning/initial-authority")
                        .header("X-Service-Name", "school-service")
                        .header("X-Internal-Api-Key", "test-key")
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProvisioningJson()))
                .andExpect(status().isOk());
    }

    @Test
    void academicServiceIsDenied() throws Exception {
        mockMvc.perform(post("/internal/v1/school-provisioning/initial-authority")
                        .header("X-Service-Name", "academic-service")
                        .header("X-Internal-Api-Key", "test-key")
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProvisioningJson()))
                .andExpect(status().isForbidden());
    }

    private static String validProvisioningJson() {
        return """
                {
                  "organizationId": 10,
                  "schoolCode": "DWN",
                  "schoolName": "Dawnrise School",
                  "schoolEmail": "school@dawnrise.test",
                  "authority": {
                    "username": "authority01",
                    "firstName": "Anita",
                    "middleName": "",
                    "lastName": "Sharma",
                    "email": "authority@dawnrise.test",
                    "phone": ""
                  }
                }
                """;
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        OrganizationProvisioningService organizationProvisioningService() {
            return new StubOrganizationProvisioningService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .build();
        }
    }

    static class StubOrganizationProvisioningService
            implements OrganizationProvisioningService {

        @Override
        public ProvisionOrganizationResponse provision(
                String idempotencyKey,
                ProvisionOrganizationRequest request
        ) {
            return new ProvisionOrganizationResponse(
                    request.getOrganizationId(),
                    OrganizationStatus.ACTIVE,
                    99L,
                    UserStatus.PENDING_ACTIVATION,
                    ProvisioningRequestStatus.SUCCEEDED
            );
        }
    }
}
