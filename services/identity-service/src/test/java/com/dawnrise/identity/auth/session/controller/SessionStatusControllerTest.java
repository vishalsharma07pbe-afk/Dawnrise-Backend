package com.dawnrise.identity.auth.session.controller;

import com.dawnrise.identity.auth.model.IdentityType;
import com.dawnrise.identity.auth.refreshtoken.cookie.RefreshTokenCookieService;
import com.dawnrise.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenSession;
import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.auth.service.AuthService;
import com.dawnrise.identity.auth.session.exception.AuthenticatedSessionLockedException;
import com.dawnrise.identity.auth.session.exception.RecentAuthenticationRequiredException;
import com.dawnrise.identity.auth.session.service.AuthenticatedSessionService;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.platform.auth.controller.PlatformAuthController;
import com.dawnrise.identity.platform.auth.refreshtoken.cookie.PlatformRefreshTokenCookieService;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenSession;
import com.dawnrise.identity.platform.auth.refreshtoken.service.PlatformRefreshTokenService;
import com.dawnrise.identity.platform.auth.service.PlatformAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SessionStatusControllerTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final Long OWNER_ID = 41L;

    @Mock
    private AuthenticatedSessionService sessions;

    @Mock
    private RefreshTokenService refreshTokens;

    @Mock
    private AuthService auth;

    @Mock
    private RefreshTokenCookieService cookies;

    @Mock
    private PlatformAuthService platformAuth;

    @Mock
    private PlatformRefreshTokenCookieService platformCookies;

    @Mock
    private PlatformRefreshTokenService platformRefreshTokens;

    private MockMvc tenantMockMvc;
    private MockMvc platformMockMvc;

    @BeforeEach
    void setUp() {
        tenantMockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthenticatedSessionController(
                                sessions,
                                refreshTokens,
                                auth,
                                cookies
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        platformMockMvc = MockMvcBuilders.standaloneSetup(
                        new PlatformAuthController(
                                platformAuth,
                                platformCookies,
                                sessions,
                                platformRefreshTokens
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void tenantStatus_whenDatabaseValidationFails_returnsServiceUnavailable()
            throws Exception {
        when(cookies.extractToken(any())).thenReturn("tenant-refresh");
        when(refreshTokens.resolveRefreshTokenSession("tenant-refresh"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        tenantMockMvc.perform(get("/api/v1/auth/session/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("SESSION_VALIDATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Session validation is unavailable"));
    }

    @Test
    void tenantStatus_whenRefreshCredentialIsInvalid_remainsUnauthorized()
            throws Exception {
        when(cookies.extractToken(any())).thenReturn("invalid");
        when(refreshTokens.resolveRefreshTokenSession("invalid"))
                .thenThrow(new InvalidRefreshTokenException("Invalid refresh token"));

        tenantMockMvc.perform(get("/api/v1/auth/session/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void tenantStatus_whenSessionIsLocked_remainsLocked() throws Exception {
        when(cookies.extractToken(any())).thenReturn("tenant-refresh");
        when(refreshTokens.resolveRefreshTokenSession("tenant-refresh"))
                .thenReturn(new RefreshTokenSession(OWNER_ID, SESSION_ID));
        when(sessions.isLocked(
                SESSION_ID,
                IdentityType.ORGANIZATION_USER,
                OWNER_ID
        )).thenThrow(new AuthenticatedSessionLockedException("Workspace is locked"));

        tenantMockMvc.perform(get("/api/v1/auth/session/status"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.validationErrors.code")
                        .value("SESSION_LOCKED"));
    }

    @Test
    void platformStatus_whenTransactionValidationFails_returnsServiceUnavailable()
            throws Exception {
        when(platformCookies.extractToken(any())).thenReturn("platform-refresh");
        when(platformRefreshTokens.resolveRefreshTokenSession("platform-refresh"))
                .thenThrow(new TransactionSystemException("transaction unavailable"));

        platformMockMvc.perform(get("/api/v1/platform/auth/session/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("SESSION_VALIDATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Session validation is unavailable"));
    }

    @Test
    void platformStatus_whenRefreshCredentialIsInvalid_remainsUnauthorized()
            throws Exception {
        when(platformCookies.extractToken(any())).thenReturn("invalid");
        when(platformRefreshTokens.resolveRefreshTokenSession("invalid"))
                .thenThrow(new InvalidRefreshTokenException("Invalid refresh token"));

        platformMockMvc.perform(get("/api/v1/platform/auth/session/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void platformStatus_whenSessionIsLocked_remainsLocked() throws Exception {
        when(platformCookies.extractToken(any())).thenReturn("platform-refresh");
        when(platformRefreshTokens.resolveRefreshTokenSession("platform-refresh"))
                .thenReturn(new PlatformRefreshTokenSession(OWNER_ID, SESSION_ID));
        when(sessions.isLocked(
                SESSION_ID,
                IdentityType.PLATFORM_USER,
                OWNER_ID
        )).thenThrow(new AuthenticatedSessionLockedException("Workspace is locked"));

        platformMockMvc.perform(get("/api/v1/platform/auth/session/status"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.validationErrors.code")
                        .value("SESSION_LOCKED"));
    }

    @Test
    void recentAuthentication_whenRecent_returnsNoContent() throws Exception {
        tenantMockMvc.perform(post("/internal/v1/authenticated-sessions/{id}/recent-authentication", SESSION_ID)
                        .queryParam("identityType", "ORGANIZATION_USER").queryParam("ownerId", OWNER_ID.toString()))
                .andExpect(status().isNoContent());
        verify(sessions).requireRecentAuthentication(SESSION_ID, IdentityType.ORGANIZATION_USER, OWNER_ID);
    }

    @Test
    void recentAuthentication_whenStale_returnsForbidden() throws Exception {
        doThrow(new RecentAuthenticationRequiredException("Recent authentication is required"))
                .when(sessions).requireRecentAuthentication(SESSION_ID, IdentityType.ORGANIZATION_USER, OWNER_ID);
        tenantMockMvc.perform(post("/internal/v1/authenticated-sessions/{id}/recent-authentication", SESSION_ID)
                        .queryParam("identityType", "ORGANIZATION_USER").queryParam("ownerId", OWNER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.validationErrors.code").value("RECENT_AUTHENTICATION_REQUIRED"));
    }

    @Test
    void recentAuthentication_whenLocked_returnsLocked() throws Exception {
        doThrow(new AuthenticatedSessionLockedException("Workspace is locked")).when(sessions)
                .requireRecentAuthentication(SESSION_ID, IdentityType.ORGANIZATION_USER, OWNER_ID);
        tenantMockMvc.perform(post("/internal/v1/authenticated-sessions/{id}/recent-authentication", SESSION_ID)
                        .queryParam("identityType", "ORGANIZATION_USER").queryParam("ownerId", OWNER_ID.toString()))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.validationErrors.code").value("SESSION_LOCKED"));
    }

    @Test
    void recentAuthentication_whenInfrastructureFails_returnsUnavailable() throws Exception {
        doThrow(new DataAccessResourceFailureException("down")).when(sessions)
                .requireRecentAuthentication(SESSION_ID, IdentityType.ORGANIZATION_USER, OWNER_ID);
        tenantMockMvc.perform(post("/internal/v1/authenticated-sessions/{id}/recent-authentication", SESSION_ID)
                        .queryParam("identityType", "ORGANIZATION_USER").queryParam("ownerId", OWNER_ID.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SESSION_VALIDATION_UNAVAILABLE"));
    }

    @Test
    void recentAuthentication_rejectsMalformedInputs() throws Exception {
        tenantMockMvc.perform(post("/internal/v1/authenticated-sessions/not-a-uuid/recent-authentication")
                        .queryParam("identityType", "ORGANIZATION_USER").queryParam("ownerId", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activityOwnerId_isPositiveConstrained() throws Exception {
        Method activity = AuthenticatedSessionController.class.getMethod(
                "validate",
                UUID.class,
                IdentityType.class,
                Long.class
        );

        assertTrue(activity.getParameters()[2]
                .isAnnotationPresent(jakarta.validation.constraints.Positive.class));
    }
}
