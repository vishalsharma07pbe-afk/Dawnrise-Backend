package com.dawnrise.identity.organization.provisioning.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class InternalServiceAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX =
            "/internal/";

    private static final String API_KEY_HEADER =
            "X-Internal-Api-Key";

    private static final String SERVICE_NAME_HEADER =
            "X-Service-Name";

    private final InternalServiceSecurityProperties properties;

    public InternalServiceAuthenticationFilter(
            InternalServiceSecurityProperties properties
    ) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        return !request.getRequestURI()
                .startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String configuredApiKey = properties.getApiKey();
        String configuredServiceName =
                properties.getAllowedServiceName();

        String submittedApiKey =
                request.getHeader(API_KEY_HEADER);
        String submittedServiceName =
                request.getHeader(SERVICE_NAME_HEADER);

        boolean validConfiguration =
                configuredApiKey != null
                        && !configuredApiKey.isBlank()
                        && configuredServiceName != null
                        && !configuredServiceName.isBlank();

        boolean validCredentials =
                validConfiguration
                        && constantTimeEquals(
                        configuredApiKey,
                        submittedApiKey
                )
                        && constantTimeEquals(
                        configuredServiceName,
                        submittedServiceName
                );

        if (!validCredentials) {
            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid internal service credentials"
            );
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        submittedServiceName,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_INTERNAL_SERVICE"
                                )
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(
            String expected,
            String submitted
    ) {
        if (expected == null || submitted == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8)
        );
    }
}
