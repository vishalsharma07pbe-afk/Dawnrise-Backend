package com.dawnrise.identity.roleapproval.controller;

import com.dawnrise.identity.auth.security.TenantSecurity;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentRequestResponse;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleapproval.service.RoleAssignmentApprovalService;
import com.dawnrise.identity.roleapproval.service.RoleAssignmentRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
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

@WebMvcTest(RoleAssignmentRequestController.class)
@Import({
        SecurityConfig.class,
        TenantSecurity.class,
        GlobalExceptionHandler.class
})
class RoleAssignmentRequestControllerSecurityIntegrationTest {

    private static final String BASE_URL =
            "/api/v1/organizations/{organizationId}/role-requests";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RoleAssignmentRequestService requestService;

    @MockitoBean
    private RoleAssignmentApprovalService approvalService;

    @Test
    void createSensitiveRequest_withPermission_succeeds() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of(
                "ROLE_ASSIGNMENT_REQUEST_CREATE"
        ));
        when(requestService.createRequest(eq(1L), any(), any()))
                .thenReturn(response());

        mockMvc.perform(post(BASE_URL, 1L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated());

        verify(requestService).createRequest(eq(1L), any(), any());
    }

    @Test
    void createSensitiveRequest_missingPermission_returnsForbidden()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(post(BASE_URL, 1L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isForbidden());

        verify(requestService, never()).createRequest(any(), any(), any());
    }

    @Test
    void sensitiveRoleCannotBypassApprovalWorkflowViaUserRoleEndpoint()
            throws Exception {
        // The implemented HTTP surface for sensitive roles is this request endpoint.
        // Direct assignment bypass is covered by UserServiceImplTest.
        createSensitiveRequest_withPermission_succeeds();
    }

    @Test
    void viewRequest_requiresViewPermission() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(get(BASE_URL + "/{requestId}", 1L, 100L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewRequest_withViewPermission_reachesService() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of(
                "ROLE_ASSIGNMENT_REQUEST_VIEW"
        ));

        mockMvc.perform(get(BASE_URL + "/{requestId}", 1L, 100L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(requestService).getRequestDetails(1L, 100L, 99L);
    }

    @Test
    void actionableRequests_requiresViewPermission() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(get(BASE_URL + "/actionable", 1L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void decisionHistory_requiresViewPermission() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(get(BASE_URL + "/decision-history", 1L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelRequest_requiresCancelPermission() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(post(BASE_URL + "/{requestId}/cancel", 1L, 100L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelRequest_withCancelPermission_reachesService()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of(
                "ROLE_ASSIGNMENT_REQUEST_CANCEL"
        ));
        when(requestService.cancelRequest(eq(1L), eq(100L), any()))
                .thenReturn(response());

        mockMvc.perform(post(BASE_URL + "/{requestId}/cancel", 1L, 100L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void decision_requiresApprovalPermission() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(post(BASE_URL + "/{requestId}/decisions", 1L, 100L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void decision_withApprovalPermission_reachesService() throws Exception {
        useJwt("token", 1L, 99L, Set.of("ADMIN"), Set.of(
                "ROLE_ASSIGNMENT_APPROVE"
        ));

        mockMvc.perform(post(BASE_URL + "/{requestId}/decisions", 1L, 100L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody()))
                .andExpect(status().isCreated());

        verify(approvalService).recordDecision(eq(1L), eq(100L), any(), any());
    }

    @Test
    void approvalPermissionDoesNotBypassPolicy() throws Exception {
        useJwt("token", 1L, 99L, Set.of("TEACHER"), Set.of(
                "ROLE_ASSIGNMENT_APPROVE"
        ));
        when(approvalService.recordDecision(eq(1L), eq(100L), any(), any()))
                .thenThrow(new ApprovalNotAllowedException(
                        "You are not authorized to review this role request"
                ));

        mockMvc.perform(post(BASE_URL + "/{requestId}/decisions", 1L, 100L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requesterCannotApproveOwnRequest() throws Exception {
        useJwt("token", 1L, 10L, Set.of("ADMIN"), Set.of(
                "ROLE_ASSIGNMENT_APPROVE"
        ));
        when(approvalService.recordDecision(eq(1L), eq(100L), any(), any()))
                .thenThrow(new ApprovalNotAllowedException(
                        "The requester cannot approve their own request"
                ));

        mockMvc.perform(post(BASE_URL + "/{requestId}/decisions", 1L, 100L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantRoleRequestAccess_returnsForbidden() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of(
                "ROLE_ASSIGNMENT_REQUEST_VIEW"
        ));

        mockMvc.perform(get(BASE_URL + "/{requestId}", 2L, 100L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidJwt_returnsUnauthorized() throws Exception {
        when(jwtDecoder.decode("bad-token"))
                .thenThrow(new BadJwtException("invalid"));

        mockMvc.perform(get(BASE_URL + "/{requestId}", 1L, 100L)
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    private void useJwt(
            String token,
            Long organizationId,
            Long subject,
            Set<String> roles,
            Set<String> permissions
    ) {
        when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("organizationId", organizationId)
                .claim("roles", List.copyOf(roles))
                .claim("permissions", List.copyOf(permissions))
                .build());
    }

    private static RoleAssignmentRequestResponse response() {
        return new RoleAssignmentRequestResponse(
                100L,
                1L,
                20L,
                com.dawnrise.identity.user.enums.UserRole.ADMIN,
                99L,
                "Needs admin access",
                ApprovalStatus.PENDING,
                Instant.now().atOffset(java.time.ZoneOffset.UTC),
                null,
                null
        );
    }

    private static String createRequestBody() {
        return """
                {
                  "userId": 20,
                  "requestedRole": "ADMIN",
                  "reason": "Needs admin access"
                }
                """;
    }

    private static String decisionBody() {
        return """
                {
                  "decision": "APPROVED",
                  "remarks": "Looks valid"
                }
                """;
    }
}
