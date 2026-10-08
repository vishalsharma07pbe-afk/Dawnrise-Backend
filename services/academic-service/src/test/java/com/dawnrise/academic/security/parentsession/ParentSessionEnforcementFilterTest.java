package com.dawnrise.academic.security.parentsession;

import com.dawnrise.academic.common.integration.identity.IdentityServiceProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
    void failsClosedAsUnavailableWhenIdentityCannotValidate() throws Exception {
        Fixture fixture = fixture();
        fixture.server.expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(parentRequest(), response, mock(FilterChain.class));

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
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("7")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("sessionId", UUID.randomUUID().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_PARENT"))
        ));
        return new MockHttpServletRequest("GET", "/api/v1/academic-years");
    }

    private record Fixture(
            ParentSessionEnforcementFilter filter,
            MockRestServiceServer server
    ) {}
}
