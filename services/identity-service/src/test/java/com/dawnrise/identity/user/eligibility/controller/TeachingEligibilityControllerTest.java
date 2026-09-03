package com.dawnrise.identity.user.eligibility.controller;

import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import com.dawnrise.identity.user.eligibility.dto.TeachingEligibilityResponse;
import com.dawnrise.identity.user.eligibility.enums.TeachingEligibilityReason;
import com.dawnrise.identity.user.eligibility.service.TeachingEligibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = TeachingEligibilityController.class,
        properties = {
                "security.internal.api-key=test-key",
                "security.internal.allowed-service-names[0]=school-service",
                "security.internal.allowed-service-names[1]=academic-service"
        }
)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        TeachingEligibilityControllerTest.TestConfig.class
})
class TeachingEligibilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTeachingEligibilityService eligibilityService;

    @Test
    void academicServiceWithInternalRoleCanAccess() throws Exception {
        eligibilityService.response = new TeachingEligibilityResponse(
                20L,
                10L,
                "Anita Sharma",
                true,
                TeachingEligibilityReason.ELIGIBLE
        );

        mockMvc.perform(get(
                        "/internal/v1/organizations/10"
                                + "/users/20/teaching-eligibility"
                )
                        .header("X-Service-Name", "academic-service")
                        .header("X-Internal-Api-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(20))
                .andExpect(jsonPath("$.organizationId").value(10))
                .andExpect(jsonPath("$.eligible").value(true));

        assertEquals(10L, eligibilityService.lastOrganizationId);
        assertEquals(20L, eligibilityService.lastUserId);
    }

    @Test
    void schoolServiceIsDenied() throws Exception {
        mockMvc.perform(get(
                        "/internal/v1/organizations/10"
                                + "/users/20/teaching-eligibility"
                )
                        .header("X-Service-Name", "school-service")
                        .header("X-Internal-Api-Key", "test-key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCallerIsDenied() throws Exception {
        mockMvc.perform(get(
                        "/internal/v1/organizations/10"
                                + "/users/20/teaching-eligibility"
                ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtOnlyCallerIsDenied() throws Exception {
        mockMvc.perform(get(
                        "/internal/v1/organizations/10"
                                + "/users/20/teaching-eligibility"
                )
                        .with(jwt()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pathIdsMustBePositive() throws Exception {
        mockMvc.perform(get(
                        "/internal/v1/organizations/0"
                                + "/users/20/teaching-eligibility"
                )
                        .header("X-Service-Name", "academic-service")
                        .header("X-Internal-Api-Key", "test-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));

        mockMvc.perform(get(
                        "/internal/v1/organizations/10"
                                + "/users/0/teaching-eligibility"
                )
                        .header("X-Service-Name", "academic-service")
                        .header("X-Internal-Api-Key", "test-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubTeachingEligibilityService teachingEligibilityService() {
            return new StubTeachingEligibilityService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .build();
        }
    }

    static class StubTeachingEligibilityService
            implements TeachingEligibilityService {

        private TeachingEligibilityResponse response;
        private long lastOrganizationId;
        private long lastUserId;

        @Override
        public TeachingEligibilityResponse check(
                long organizationId,
                long userId
        ) {
            lastOrganizationId = organizationId;
            lastUserId = userId;
            return response;
        }

        @Override
        public List<TeachingEligibilityResponse> checkBatch(
                long organizationId,
                List<Long> userIds
        ) {
            lastOrganizationId = organizationId;
            return List.of();
        }
    }
}
