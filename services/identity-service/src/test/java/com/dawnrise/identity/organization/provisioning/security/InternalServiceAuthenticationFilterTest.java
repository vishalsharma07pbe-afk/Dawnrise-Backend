package com.dawnrise.identity.organization.provisioning.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalServiceAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void schoolServiceIsAccepted() throws Exception {
        doFilter("school-service", "test-key", properties(
                List.of("school-service", "academic-service")
        ));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        assertEquals("school-service", authentication.getName());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority()
                        .equals("ROLE_INTERNAL_SERVICE")));
    }

    @Test
    void academicServiceIsAccepted() throws Exception {
        doFilter("academic-service", "test-key", properties(
                List.of("school-service", "academic-service")
        ));

        assertEquals(
                "academic-service",
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName()
        );
    }

    @Test
    void unlistedServiceIsRejected() throws Exception {
        MockHttpServletResponse response =
                doFilter("billing-service", "test-key", properties(
                        List.of("school-service", "academic-service")
                ));

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void wrongOrMissingApiKeyIsRejected() throws Exception {
        MockHttpServletResponse wrongKeyResponse =
                doFilter("academic-service", "wrong", properties(
                        List.of("academic-service")
                ));
        assertEquals(401, wrongKeyResponse.getStatus());

        MockHttpServletResponse missingKeyResponse =
                doFilter("academic-service", null, properties(
                        List.of("academic-service")
                ));
        assertEquals(401, missingKeyResponse.getStatus());
    }

    @Test
    void missingServiceNameIsRejected() throws Exception {
        MockHttpServletResponse response =
                doFilter(null, "test-key", properties(
                        List.of("academic-service")
                ));

        assertEquals(401, response.getStatus());
    }

    @Test
    void emptyConfigurationIsRejected() throws Exception {
        MockHttpServletResponse missingKeyResponse =
                doFilter("academic-service", "test-key", properties(
                        List.of("academic-service"),
                        null
                ));
        assertEquals(401, missingKeyResponse.getStatus());

        MockHttpServletResponse noAllowedServicesResponse =
                doFilter("academic-service", "test-key", properties(
                        List.of()
                ));
        assertEquals(401, noAllowedServicesResponse.getStatus());
    }

    @Test
    void propertiesUsePluralAllowedServiceNames() {
        InternalServiceSecurityProperties properties =
                new InternalServiceSecurityProperties();
        properties.setAllowedServiceNames(List.of(
                "school-service",
                "academic-service"
        ));

        assertEquals(
                List.of("school-service", "academic-service"),
                properties.getAllowedServiceNames()
        );
    }

    @Test
    void nonInternalPathsAreIgnored() throws Exception {
        InternalServiceAuthenticationFilter filter =
                new InternalServiceAuthenticationFilter(properties(
                        List.of("academic-service")
                ));
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(response.isCommitted());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private static MockHttpServletResponse doFilter(
            String serviceName,
            String apiKey,
            InternalServiceSecurityProperties properties
    ) throws ServletException, IOException {
        InternalServiceAuthenticationFilter filter =
                new InternalServiceAuthenticationFilter(properties);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/v1/test");
        if (serviceName != null) {
            request.addHeader("X-Service-Name", serviceName);
        }
        if (apiKey != null) {
            request.addHeader("X-Internal-Api-Key", apiKey);
        }
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        return response;
    }

    private static InternalServiceSecurityProperties properties(
            List<String> allowedServiceNames
    ) {
        return properties(allowedServiceNames, "test-key");
    }

    private static InternalServiceSecurityProperties properties(
            List<String> allowedServiceNames,
            String apiKey
    ) {
        InternalServiceSecurityProperties properties =
                new InternalServiceSecurityProperties();
        properties.setApiKey(apiKey);
        properties.setAllowedServiceNames(allowedServiceNames);
        return properties;
    }
}
