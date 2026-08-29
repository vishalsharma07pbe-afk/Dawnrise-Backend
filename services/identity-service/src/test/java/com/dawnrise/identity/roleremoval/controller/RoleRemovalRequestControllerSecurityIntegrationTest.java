package com.dawnrise.identity.roleremoval.controller;

import com.dawnrise.identity.auth.security.TenantSecurity;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalRequestResponse;
import com.dawnrise.identity.roleremoval.service.RoleRemovalApprovalService;
import com.dawnrise.identity.roleremoval.service.RoleRemovalRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleRemovalRequestController.class)
@Import({
        SecurityConfig.class,
        TenantSecurity.class,
        GlobalExceptionHandler.class
})
class RoleRemovalRequestControllerSecurityIntegrationTest {

    private static final String BASE_URL =
            "/api/v1/organizations/{organizationId}/role-removal-requests";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private RoleRemovalRequestService requestService;
    @MockitoBean
    private RoleRemovalApprovalService approvalService;

    @Test
    void unauthenticatedRequest_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_URL + "/actionable", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequest_missingPermission_returnsForbidden() throws Exception {
        useJwt("token", 1L, 99L, Set.of());

        mockMvc.perform(post(BASE_URL, 1L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isForbidden());

        verify(requestService, never()).createRequest(any(), any(), any());
    }

    @Test
    void createRequest_wrongTenant_returnsForbidden() throws Exception {
        useJwt("token", 2L, 99L, Set.of(
                "ROLE_REMOVAL_REQUEST_CREATE"
        ));

        mockMvc.perform(post(BASE_URL, 1L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isForbidden());

        verify(requestService, never()).createRequest(any(), any(), any());
    }

    @Test
    void createRequest_withPermission_reachesWorkflow() throws Exception {
        useJwt("token", 1L, 99L, Set.of(
                "ROLE_REMOVAL_REQUEST_CREATE"
        ));
        when(requestService.createRequest(eq(1L), any(), any()))
                .thenReturn(new RoleRemovalRequestResponse());

        mockMvc.perform(post(BASE_URL, 1L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated());

        verify(requestService).createRequest(eq(1L), any(), any());
    }

    @Test
    void decision_missingPermission_returnsForbidden() throws Exception {
        useJwt("token", 1L, 99L, Set.of());

        mockMvc.perform(post(BASE_URL + "/{requestId}/decisions", 1L, 100L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody()))
                .andExpect(status().isForbidden());

        verify(approvalService, never())
                .recordDecision(any(), any(), any(), any());
    }

    @Test
    void decision_permissionDoesNotBypassApprovalPolicy() throws Exception {
        useJwt("token", 1L, 99L, Set.of("ROLE_REMOVAL_APPROVE"));
        when(approvalService.recordDecision(eq(1L), eq(100L), any(), any()))
                .thenThrow(new ApprovalNotAllowedException(
                        "You are not authorized to review this role removal request"
                ));

        mockMvc.perform(post(BASE_URL + "/{requestId}/decisions", 1L, 100L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody()))
                .andExpect(status().isForbidden());
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

    private String createRequestBody() {
        return """
                {
                  "userId": 20,
                  "requestedRole": "ADMIN",
                  "reason": "Governance transition"
                }
                """;
    }

    private String decisionBody() {
        return """
                {
                  "decision": "APPROVED",
                  "remarks": "Approved"
                }
                """;
    }
}
