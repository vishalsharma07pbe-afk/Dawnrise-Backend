package com.dawnrise.identity.platform.user.controller;

import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import com.dawnrise.identity.platform.auth.security.PlatformTokenSecurity;
import com.dawnrise.identity.platform.user.service.PlatformUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlatformUserController.class)
@Import({
        SecurityConfig.class,
        PlatformTokenSecurity.class,
        GlobalExceptionHandler.class
})
class PlatformUserControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PlatformUserService platformUserService;

    @Test
    void managementEndpoint_whenUnauthenticated_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(get("/api/v1/platform/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managementEndpoint_whenJwtInvalid_returnsUnauthorized()
            throws Exception {
        when(jwtDecoder.decode("bad-token"))
                .thenThrow(new BadJwtException("invalid"));

        mockMvc.perform(get("/api/v1/platform/users")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managementEndpoint_whenOrganizationToken_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("org-token"))
                .thenReturn(jwt(
                        "org-token",
                        "ORGANIZATION_USER",
                        List.of(),
                        Set.of("PLATFORM_USER_VIEW")
                ));

        mockMvc.perform(get("/api/v1/platform/users")
                        .header("Authorization", "Bearer org-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(platformUserService);
    }

    @Test
    void listUsers_whenPlatformTokenMissingPermission_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("platform-token"))
                .thenReturn(jwt(
                        "platform-token",
                        "PLATFORM_USER",
                        List.of("dawnrise-operations"),
                        Set.of()
                ));

        mockMvc.perform(get("/api/v1/platform/users")
                        .header(
                                "Authorization",
                                "Bearer platform-token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(platformUserService);
    }

    @Test
    void listUsers_whenPlatformTokenHasPermission_reachesService()
            throws Exception {
        when(jwtDecoder.decode("platform-token"))
                .thenReturn(jwt(
                        "platform-token",
                        "PLATFORM_USER",
                        List.of("dawnrise-operations"),
                        Set.of("PLATFORM_USER_VIEW")
                ));

        mockMvc.perform(get("/api/v1/platform/users")
                        .header(
                                "Authorization",
                                "Bearer platform-token"
                        ))
                .andExpect(status().isOk());

        verify(platformUserService).getPlatformUsers(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void managementEndpoint_whenPlatformTokenWrongAudience_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("wrong-audience-token"))
                .thenReturn(jwt(
                        "wrong-audience-token",
                        "PLATFORM_USER",
                        List.of("school-management"),
                        Set.of("PLATFORM_USER_VIEW")
                ));

        mockMvc.perform(get("/api/v1/platform/users")
                        .header(
                                "Authorization",
                                "Bearer wrong-audience-token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(platformUserService);
    }

    @Test
    void managementEndpoint_whenIdentityTypeMissing_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("legacy-org-token"))
                .thenReturn(jwt(
                        "legacy-org-token",
                        null,
                        List.of(),
                        Set.of("PLATFORM_USER_VIEW")
                ));

        mockMvc.perform(get("/api/v1/platform/users")
                        .header(
                                "Authorization",
                                "Bearer legacy-org-token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(platformUserService);
    }

    private static Jwt jwt(
            String tokenValue,
            String identityType,
            List<String> audience,
            Set<String> permissions
    ) {
        Jwt.Builder builder = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .issuer("dawnrise-identity-service")
                .subject("42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("username", "employee01")
                .claim("roles", Set.of("SALES"))
                .claim("permissions", permissions);

        if (identityType != null) {
            builder.claim("identityType", identityType);
        }

        if (!audience.isEmpty()) {
            builder.audience(audience);
        }

        return builder.build();
    }
}
