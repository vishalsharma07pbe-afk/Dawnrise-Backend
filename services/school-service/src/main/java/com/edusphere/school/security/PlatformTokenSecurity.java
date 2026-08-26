package com.edusphere.school.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component("platformTokenSecurity")
public class PlatformTokenSecurity {

    private static final String PLATFORM_IDENTITY_TYPE =
            "PLATFORM_USER";

    private static final String PLATFORM_AUDIENCE =
            "dawnrise-operations";

    public boolean hasPermissions(
            Authentication authentication,
            String... requiredPermissions
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
            return false;
        }

        if (!authentication.isAuthenticated()) {
            return false;
        }

        String identityType = jwtToken
                .getToken()
                .getClaimAsString("identityType");

        if (!PLATFORM_IDENTITY_TYPE.equals(identityType)) {
            return false;
        }

        if (!jwtToken
                .getToken()
                .getAudience()
                .contains(PLATFORM_AUDIENCE)) {
            return false;
        }

        Set<String> authorities = authentication
                .getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());

        return Arrays
                .stream(requiredPermissions)
                .allMatch(authorities::contains);
    }
}