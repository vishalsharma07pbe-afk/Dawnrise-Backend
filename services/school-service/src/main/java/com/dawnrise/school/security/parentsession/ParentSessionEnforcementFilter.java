package com.dawnrise.school.security.parentsession;

import com.dawnrise.school.school.provisioning.IdentityProvisioningProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class ParentSessionEnforcementFilter extends OncePerRequestFilter {
    private final RestClient client;
    private final IdentityProvisioningProperties properties;

    public ParentSessionEnforcementFilter(RestClient client, IdentityProvisioningProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)
                || !"ORGANIZATION_USER".equals(token.getToken().getClaimAsString("identityType"))) {
            chain.doFilter(request, response);
            return;
        }
        String sessionIdClaim = token.getToken().getClaimAsString("sessionId");
        String ownerIdClaim = token.getToken().getSubject();
        UUID sessionId;
        Long ownerId;
        try {
            sessionId = UUID.fromString(sessionIdClaim);
            ownerId = Long.valueOf(ownerIdClaim);
            if (ownerId <= 0) {
                throw new IllegalArgumentException("Owner ID must be positive");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            reject(response, 401, "INVALID_SESSION_CLAIMS", "Session claims are missing or invalid");
            return;
        }
        try {
            client.post().uri(builder -> builder
                            .path("/internal/v1/authenticated-sessions/{id}/activity")
                            .queryParam("identityType", "ORGANIZATION_USER")
                            .queryParam("ownerId", ownerId).build(sessionId))
                    .header("X-Internal-Api-Key", properties.getApiKey())
                    .header("X-Service-Name", properties.getServiceName())
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 423) {
                reject(response, 423, "SESSION_LOCKED", "Workspace is locked");
            } else {
                reject(response, 503, "SESSION_VALIDATION_UNAVAILABLE", "Session validation is unavailable");
            }
            return;
        } catch (RestClientException exception) {
            reject(response, 503, "SESSION_VALIDATION_UNAVAILABLE", "Session validation is unavailable");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
