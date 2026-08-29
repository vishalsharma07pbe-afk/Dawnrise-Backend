package com.dawnrise.identity.securityaudit.controller;

import com.dawnrise.identity.auth.security.TenantSecurity;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityAuditController.class)
@Import({
        SecurityConfig.class,
        TenantSecurity.class,
        GlobalExceptionHandler.class
})
class SecurityAuditControllerSecurityIntegrationTest {

    private static final String BASE_URL =
            "/api/v1/organizations/{organizationId}/security-audit-events";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private SecurityAuditService auditService;

    @Test
    void unauthenticatedRequest_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_URL, 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingPermission_returnsForbidden() throws Exception {
        useJwt("token", 1L, 10L, Set.of());

        mockMvc.perform(get(BASE_URL, 1L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(auditService, never()).getEvents(any(), any());
    }

    @Test
    void wrongTenant_returnsForbidden() throws Exception {
        useJwt("token", 2L, 10L, Set.of("SECURITY_AUDIT_VIEW"));

        mockMvc.perform(get(BASE_URL, 1L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(auditService, never()).getEvents(any(), any());
    }

    @Test
    void correctPermission_allowsTenantScopedAccess() throws Exception {
        useJwt("token", 1L, 10L, Set.of("SECURITY_AUDIT_VIEW"));
        when(auditService.getEvents(eq(1L), any()))
                .thenReturn(new PageResponse<>(
                        List.of(),
                        0,
                        20,
                        0,
                        0,
                        true,
                        true,
                        true
                ));

        mockMvc.perform(get(BASE_URL, 1L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(auditService).getEvents(eq(1L), any());
    }

    private void useJwt(
            String tokenValue,
            Long organizationId,
            Long userId,
            Set<String> permissions
    ) {
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .claim("organizationId", organizationId)
                .claim("permissions", List.copyOf(permissions))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);
    }
}
