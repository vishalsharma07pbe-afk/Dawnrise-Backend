package com.dawnrise.identity.profilechange.controller;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.profilechange.dto.ProfileChangeRequestResponse;
import com.dawnrise.identity.profilechange.enums.ProfileChangeRequestStatus;
import com.dawnrise.identity.profilechange.service.ProfileChangeRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileChangeRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProfileChangeRequestControllerTest {

    private static final String BASE_URL =
            "/api/v1/organizations/{organizationId}/profile-change-requests";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileChangeRequestService requestService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(jwtPrincipal("20"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRequest_whenValid_returnsCreatedRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        ProfileChangeRequestResponse response = response(requestId);

        when(requestService.createRequest(eq(1L), eq(20L), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/users/{targetUserId}", 1L, 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Rohan",
                                  "lastName": "Sharma",
                                  "email": "rohan@dawnrise.com",
                                  "phone": "+91 9999999999",
                                  "reason": "Legal name correction"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.requestedByName").value("Admin"))
                .andExpect(jsonPath("$.proposedFirstName").value("Rohan"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(requestService)
                .createRequest(eq(1L), eq(20L), any(), any());
    }

    @Test
    void createRequest_whenInvalid_returnsValidationErrors() throws Exception {
        mockMvc.perform(post(BASE_URL + "/users/{targetUserId}", 1L, 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "",
                                  "email": "invalid-email",
                                  "phone": "12",
                                  "reason": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.firstName")
                        .value("First name is required"))
                .andExpect(jsonPath("$.validationErrors.email")
                        .value("Email must be valid"))
                .andExpect(jsonPath("$.validationErrors.phone")
                        .value("Phone number must be valid"))
                .andExpect(jsonPath("$.validationErrors.reason")
                        .value("Reason is required"));

        verify(requestService, never())
                .createRequest(any(), any(), any(), any());
    }

    @Test
    void getTargetUserRequests_whenAuthenticated_returnsPagedRequests()
            throws Exception {
        UUID requestId = UUID.randomUUID();
        PageResponse<ProfileChangeRequestResponse> page =
                new PageResponse<>(
                        List.of(response(requestId)),
                        0,
                        20,
                        1,
                        1,
                        true,
                        true,
                        false
                );

        when(requestService.getTargetUserRequests(
                eq(1L),
                eq(20L),
                eq(ProfileChangeRequestStatus.PENDING),
                any(),
                any(Pageable.class)
        )).thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/users/{targetUserId}", 1L, 20L)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requestId")
                        .value(requestId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.pageSize").value(20));

        verify(requestService).getTargetUserRequests(
                eq(1L),
                eq(20L),
                eq(ProfileChangeRequestStatus.PENDING),
                any(),
                any(Pageable.class)
        );
    }

    @Test
    void approveRequest_whenValid_returnsApprovedRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        ProfileChangeRequestResponse response = response(
                requestId,
                ProfileChangeRequestStatus.APPROVED,
                "Looks right"
        );

        when(requestService.approveRequest(eq(1L), eq(requestId), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/{requestId}/approve", 1L, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Looks right"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionReason").value("Looks right"));

        verify(requestService)
                .approveRequest(eq(1L), eq(requestId), any(), any());
    }

    private static ProfileChangeRequestResponse response(UUID requestId) {
        return response(requestId, ProfileChangeRequestStatus.PENDING, null);
    }

    private static ProfileChangeRequestResponse response(
            UUID requestId,
            ProfileChangeRequestStatus status,
            String decisionReason
    ) {
        OffsetDateTime now =
                Instant.parse("2026-08-17T10:00:00Z")
                        .atOffset(ZoneOffset.UTC);

        return new ProfileChangeRequestResponse(
                requestId,
                "Admin",
                status == ProfileChangeRequestStatus.PENDING
                        ? null
                        : "Rohan Sharma",
                "Rahul",
                null,
                "Sharma",
                "rahul@dawnrise.com",
                null,
                "Rohan",
                null,
                "Sharma",
                "rohan@dawnrise.com",
                "+91 9999999999",
                "Legal name correction",
                status,
                decisionReason,
                status == ProfileChangeRequestStatus.PENDING ? null : now,
                now.plusDays(7),
                now,
                now
        );
    }

    private static JwtAuthenticationToken jwtPrincipal(String subject) {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub",
                        subject,
                        "permissions",
                        List.of(
                                "PROFILE_UPDATE_SELF",
                                "USER_PROFILE_UPDATE"
                        )
                )
        );
        return new JwtAuthenticationToken(jwt);
    }
}
