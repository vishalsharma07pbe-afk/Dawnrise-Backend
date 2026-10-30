package com.dawnrise.identity.notification.controller;

import com.dawnrise.identity.auth.security.TenantSecurity;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import com.dawnrise.identity.notification.exception.NotificationNotFoundException;
import com.dawnrise.identity.notification.service.NotificationService;
import com.dawnrise.identity.notification.security.NotificationSecurity;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, TenantSecurity.class, NotificationSecurity.class, GlobalExceptionHandler.class})
class NotificationControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private NotificationService notifications;
    @MockitoBean private com.dawnrise.identity.auth.session.service.AuthenticatedSessionService sessions;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformIdentityCannotReadOrganizationInbox() throws Exception {
        useJwt("platform", null, 10L, "PLATFORM_USER");
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer platform"))
                .andExpect(status().isForbidden());
        verify(notifications, never()).unreadCount(any(Long.class), any(Long.class));
    }

    @Test
    void organizationAndUserAreAlwaysTakenFromJwt() throws Exception {
        useJwt("user", 7L, 19L, "ORGANIZATION_USER");
        when(notifications.unreadCount(7L, 19L)).thenReturn(4L);
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer user"))
                .andExpect(status().isOk());
        verify(notifications).unreadCount(7L, 19L);
    }

    @Test
    void foreignRecipientIdReturnsNeutralNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        useJwt("user", 7L, 19L, "ORGANIZATION_USER");
        when(notifications.detail(7L, 19L, id)).thenThrow(new NotificationNotFoundException());
        mockMvc.perform(get("/api/v1/notifications/{id}", id)
                        .header("Authorization", "Bearer user"))
                .andExpect(status().isNotFound());
        verify(notifications).detail(eq(7L), eq(19L), eq(id));
    }

    private void useJwt(String token, Long organizationId, long userId, String identityType) {
        Jwt.Builder builder = Jwt.withTokenValue(token).header("alg", "none")
                .subject(String.valueOf(userId)).claim("identityType", identityType)
                .claim("sessionId", UUID.randomUUID().toString()).claim("permissions", List.of())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (organizationId != null) builder.claim("organizationId", organizationId);
        when(jwtDecoder.decode(token)).thenReturn(builder.build());
    }
}
