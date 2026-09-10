package com.dawnrise.school.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
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
    private final InternalServiceErrorResponseWriter errorResponseWriter;

    public InternalServiceAuthenticationFilter(
            InternalServiceSecurityProperties properties,
            InternalServiceErrorResponseWriter errorResponseWriter
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
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
        List<String> configuredServiceNames =
                properties.getAllowedServiceNames();

        String submittedApiKey =
                request.getHeader(API_KEY_HEADER);
        String submittedServiceName =
                request.getHeader(SERVICE_NAME_HEADER);

        boolean validConfiguration =
                configuredApiKey != null
                        && !configuredApiKey.isBlank()
                        && configuredServiceNames != null
                        && !configuredServiceNames.isEmpty();

        boolean validServiceName =
                validConfiguration
                        && configuredServiceNames.stream()
                        .filter(serviceName ->
                                serviceName != null
                                        && !serviceName.isBlank()
                        )
                        .anyMatch(serviceName ->
                                constantTimeEquals(
                                        serviceName,
                                        submittedServiceName
                                )
                        );

        boolean validApiKey =
                validConfiguration
                        && constantTimeEquals(
                        configuredApiKey,
                        submittedApiKey
                );

        if (!validApiKey) {
            SecurityContextHolder.clearContext();
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid internal service credentials"
            );
            return;
        }

        if (!validServiceName) {
            SecurityContextHolder.clearContext();
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "Internal service is not allowed"
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
