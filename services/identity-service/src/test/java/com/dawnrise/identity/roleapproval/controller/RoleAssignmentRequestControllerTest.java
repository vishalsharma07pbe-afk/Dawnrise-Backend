package com.dawnrise.identity.roleapproval.controller;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentApprovalResponse;
import com.dawnrise.identity.roleapproval.enums.ApprovalDecision;
import com.dawnrise.identity.roleapproval.service.RoleAssignmentApprovalService;
import com.dawnrise.identity.roleapproval.service.RoleAssignmentRequestService;
import com.dawnrise.identity.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleAssignmentRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RoleAssignmentRequestControllerTest {

    private static final String BASE_URL =
            "/api/v1/organizations/{organizationId}/role-requests";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleAssignmentRequestService requestService;

    @MockitoBean
    private RoleAssignmentApprovalService approvalService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(jwtPrincipal("30"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDecisionHistory_whenAuthenticated_returnsPagedDecisions()
            throws Exception {
        RoleAssignmentApprovalResponse decision =
                new RoleAssignmentApprovalResponse(
                        50L,
                        100L,
                        30L,
                        UserRole.ADMIN,
                        ApprovalDecision.APPROVED,
                        "Looks valid",
                        Instant.parse("2026-08-17T10:00:00Z")
                                .atOffset(java.time.ZoneOffset.UTC)
                );

        PageResponse<RoleAssignmentApprovalResponse> response =
                new PageResponse<>(
                        List.of(decision),
                        0,
                        20,
                        1,
                        1,
                        true,
                        true,
                        false
                );

        when(approvalService.getDecisionHistoryForApprover(
                eq(1L),
                eq(30L),
                any(Pageable.class)
        )).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/decision-history", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.content[0].requestId").value(100))
                .andExpect(jsonPath("$.content[0].approverUserId").value(30))
                .andExpect(jsonPath("$.content[0].approverRole")
                        .value("ADMIN"))
                .andExpect(jsonPath("$.content[0].decision")
                        .value("APPROVED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.pageSize").value(20));

        verify(approvalService).getDecisionHistoryForApprover(
                eq(1L),
                eq(30L),
                any(Pageable.class)
        );
    }

    private static JwtAuthenticationToken jwtPrincipal(String subject) {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", subject)
        );
        return new JwtAuthenticationToken(jwt);
    }
}
