package com.dawnrise.identity.config;

import com.dawnrise.identity.auth.security.TenantSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    @Test
    void jwtAuthenticationConverter_mapsRolesAndPermissionsWithExpectedPrefixes() {
        JwtAuthenticationConverter converter =
                new SecurityConfig().jwtAuthenticationConverter();

        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(jwt(
                        1L,
                        "10",
                        Set.of("ADMIN"),
                        Set.of("USER_CREATE", "PROFILE_VIEW_SELF")
                ));

        Set<String> authorities =
                authentication.getAuthorities()
                        .stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("USER_CREATE"));
        assertTrue(authorities.contains("PROFILE_VIEW_SELF"));
        assertFalse(authorities.contains("ROLE_USER_CREATE"));
    }

    @Test
    void jwtAuthenticationConverter_roleAloneDoesNotGrantPermission() {
        JwtAuthenticationConverter converter =
                new SecurityConfig().jwtAuthenticationConverter();

        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(jwt(
                        1L,
                        "10",
                        Set.of("HR"),
                        Set.of()
                ));

        Set<String> authorities =
                authentication.getAuthorities()
                        .stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_HR"));
        assertFalse(authorities.contains("USER_CREATE"));
    }

    @Test
    void jwtAuthenticationConverter_handlesMissingPermissionsClaimSafely() {
        JwtAuthenticationConverter converter =
                new SecurityConfig().jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("dawnrise-identity-service")
                .subject("10")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("organizationId", 1L)
                .claim("roles", Set.of("HR"))
                .build();

        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(jwt);

        Set<String> authorities =
                authentication.getAuthorities()
                        .stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_HR"));
        assertFalse(authorities.contains("USER_CREATE"));
        assertFalse(authorities.contains("ROLE_USER_CREATE"));
    }

    @Test
    void tenantSecurity_allowsOnlySameOrganization() {
        TenantSecurity tenantSecurity = new TenantSecurity();
        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt(
                        1L,
                        "10",
                        Set.of("ADMIN"),
                        Set.of("USER_VIEW")
                ));

        assertTrue(tenantSecurity.canAccessOrganization(authentication, 1L));
        assertFalse(tenantSecurity.canAccessOrganization(authentication, 2L));
    }

    @Test
    void tenantSecurity_allowsSelfAccessOnlyForMatchingSubject() {
        TenantSecurity tenantSecurity = new TenantSecurity();
        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt(
                        1L,
                        "10",
                        Set.of("ADMIN"),
                        Set.of("PROFILE_VIEW_SELF")
                ));

        assertTrue(tenantSecurity.isCurrentUser(authentication, 10L));
        assertFalse(tenantSecurity.isCurrentUser(authentication, 11L));
    }

    private static Jwt jwt(
            Long organizationId,
            String subject,
            Set<String> roles,
            Set<String> permissions
    ) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("dawnrise-identity-service")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("organizationId", organizationId)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
    }
}
