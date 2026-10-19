package com.dawnrise.academic.security.parentsession;

import com.dawnrise.academic.common.integration.identity.IdentityServiceProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class ParentSessionEnforcementFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesTheIdentityValidationCallAsAcademicService() throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(header("X-Service-Name", "academic-service"))
                .andRespond(withSuccess());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = parentRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        fixture.server.verify();
    }

    @Test
    void activityForbiddenFailsAsUnavailableWithoutCallingRecentAuthentication() throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(
                authenticatedRequest("POST", "/api/v1/report-cards/7/publish"),
                response,
                mock(FilterChain.class)
        );

        assertEquals(503, response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                response.getContentAsString().contains("SESSION_VALIDATION_UNAVAILABLE"));
        org.junit.jupiter.api.Assertions.assertFalse(
                response.getContentAsString().contains("RECENT_AUTHENTICATION_REQUIRED"));
        fixture.server.verify();
    }

    @ParameterizedTest
    @CsvSource({"LOCKED,423", "INTERNAL_SERVER_ERROR,503", "SERVICE_UNAVAILABLE,503"})
    void mapsActivityHttpFailures(HttpStatus status, int expected) throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(method(HttpMethod.POST)).andRespond(withStatus(status));
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(parentRequest(), response, mock(FilterChain.class));

        assertEquals(expected, response.getStatus());
        fixture.server.verify();
    }

    @Test
    void mapsActivityNetworkFailureToUnavailable() throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("offline")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(parentRequest(), response, mock(FilterChain.class));

        assertEquals(503, response.getStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/v1/grading-policies", "PUT,/api/v1/grading-policies/9",
            "POST,/api/v1/grading-policies/9/activate", "POST,/api/v1/report-cards/publications/8/generate",
            "POST,/api/v1/report-cards/7/submit", "POST,/api/v1/report-cards/7/approve",
            "POST,/api/v1/report-cards/7/return", "POST,/api/v1/report-cards/7/publish"
    })
    void sensitiveMutationsRequireAuthoritativeRecentAuthentication(String verb, String path) throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(method(HttpMethod.POST)).andRespond(withSuccess());
        fixture.server.expect(requestTo(org.hamcrest.Matchers.containsString("/recent-authentication")))
                .andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        MockHttpServletRequest request = authenticatedRequest(verb, path);
        FilterChain chain = mock(FilterChain.class);
        fixture.filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.any());
        fixture.server.verify();
    }

    @Test
    void readOnlyPdfDoesNotRequireRecentAuthentication() throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(method(HttpMethod.POST)).andRespond(withSuccess());
        MockHttpServletRequest request = authenticatedRequest("GET", "/api/v1/report-cards/7/pdf");
        FilterChain chain = mock(FilterChain.class);
        fixture.filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.any());
        fixture.server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/grading-policies", "GET,/api/v1/report-cards/7",
            "GET,/api/v1/report-cards/7/pdf", "POST,/api/v1/report-cards/7/archive",
            "DELETE,/api/v1/grading-policies/9"
    })
    void recentAuthenticationIsNotCalledForOtherRoutes(String verb, String path) throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(org.hamcrest.Matchers.containsString("/activity")))
                .andRespond(withSuccess());
        MockHttpServletRequest request = authenticatedRequest(verb, path);
        FilterChain chain = mock(FilterChain.class);

        fixture.filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.any());
        fixture.server.verify();
    }

    @ParameterizedTest
    @CsvSource({"FORBIDDEN,403", "LOCKED,423", "SERVICE_UNAVAILABLE,503"})
    void mapsRecentAuthenticationFailures(HttpStatus status, int expected) throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(method(HttpMethod.POST)).andRespond(withSuccess());
        fixture.server.expect(method(HttpMethod.POST)).andRespond(withStatus(status));
        MockHttpServletResponse response = new MockHttpServletResponse();
        fixture.filter.doFilter(authenticatedRequest("POST", "/api/v1/report-cards/7/publish"), response, mock(FilterChain.class));
        assertEquals(expected, response.getStatus());
    }

    @Test
    void mapsRecentAuthenticationNetworkFailureToUnavailable() throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(org.hamcrest.Matchers.containsString("/activity")))
                .andRespond(withSuccess());
        fixture.server.expect(requestTo(org.hamcrest.Matchers.containsString("/recent-authentication")))
                .andRespond(withException(new IOException("offline")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(
                authenticatedRequest("POST", "/api/v1/report-cards/7/approve"),
                response,
                mock(FilterChain.class)
        );

        assertEquals(503, response.getStatus());
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://identity-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IdentityServiceProperties properties = new IdentityServiceProperties();
        properties.setApiKey("test-key");
        properties.setServiceName("academic-service");
        return new Fixture(new ParentSessionEnforcementFilter(builder.build(), properties), server);
    }

    private MockHttpServletRequest parentRequest() {
        return authenticatedRequest("GET", "/api/v1/academic-years");
    }

    private MockHttpServletRequest authenticatedRequest(String method, String path) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("7")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("identityType", "ORGANIZATION_USER")
                .claim("sessionId", UUID.randomUUID().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_PARENT"))
        ));
        return new MockHttpServletRequest(method, path);
    }

    private record Fixture(
            ParentSessionEnforcementFilter filter,
            MockRestServiceServer server
    ) {}
}
