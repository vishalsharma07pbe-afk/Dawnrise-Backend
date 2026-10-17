package com.dawnrise.identity.auth.parentsession.controller;

import com.dawnrise.identity.auth.dto.LoginResponse;
import com.dawnrise.identity.auth.model.AuthenticationResult;
import com.dawnrise.identity.auth.parentsession.service.ParentSessionService;
import com.dawnrise.identity.auth.refreshtoken.cookie.RefreshTokenCookieService;
import com.dawnrise.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.dawnrise.identity.auth.service.AuthService;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParentSessionController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ParentSessionControllerTest {
    @MockitoBean
    private com.dawnrise.identity.auth.session.service.AuthenticatedSessionService authenticatedSessionService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParentSessionService parentSessionService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenCookieService cookieService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void unlock_withoutAuthorizationHeader_usesCookieAndReturnsReplacementCredentials()
            throws Exception {
        LoginResponse response = new LoginResponse(
                "replacement-access",
                "Bearer",
                3600L,
                10L,
                1L,
                "Dawnrise School",
                "parent01",
                Set.of("PARENT")
        );
        ResponseCookie replacementCookie = ResponseCookie
                .from("refreshToken", "replacement-refresh")
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();

        when(cookieService.extractToken(any())).thenReturn("old-refresh");
        when(authService.unlockParent("old-refresh", "secret"))
                .thenReturn(new AuthenticationResult(
                        response,
                        "replacement-refresh"
                ));
        when(cookieService.createCookie("replacement-refresh"))
                .thenReturn(replacementCookie);

        mockMvc.perform(post("/api/v1/auth/parent-session/unlock")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "refreshToken",
                                "old-refresh"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("replacement-access"))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        replacementCookie.toString()
                ));

        verify(authService).unlockParent("old-refresh", "secret");
    }

    @Test
    void unlock_whenRefreshCookieMissing_returnsUnauthorized() throws Exception {
        when(authService.unlockParent(null, "secret"))
                .thenThrow(new InvalidRefreshTokenException(
                        "The refresh token is invalid or expired"
                ));

        mockMvc.perform(post("/api/v1/auth/parent-session/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"secret"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("The refresh token is invalid or expired"));

        verify(cookieService, never()).createCookie(any());
    }

    @Test
    void unlock_whenRefreshCookieInvalid_returnsUnauthorized() throws Exception {
        when(cookieService.extractToken(any())).thenReturn("invalid");
        when(authService.unlockParent("invalid", "secret"))
                .thenThrow(new InvalidRefreshTokenException(
                        "The refresh token is invalid or expired"
                ));

        mockMvc.perform(post("/api/v1/auth/parent-session/unlock")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "refreshToken",
                                "invalid"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"secret"}
                                """))
                .andExpect(status().isUnauthorized());

        verify(cookieService, never()).createCookie(any());
    }

    @Test
    void unlock_withJwtForAnotherIdentity_stillUsesRefreshCookieIdentity()
            throws Exception {
        LoginResponse response = new LoginResponse(
                "cookie-owner-access",
                "Bearer",
                3600L,
                10L,
                1L,
                "Dawnrise School",
                "parent01",
                Set.of("PARENT")
        );
        ResponseCookie replacementCookie = ResponseCookie
                .from("refreshToken", "replacement-refresh")
                .httpOnly(true)
                .path("/")
                .build();

        when(cookieService.extractToken(any())).thenReturn("owner-refresh");
        when(jwtDecoder.decode("other-user-jwt")).thenReturn(new Jwt(
                "other-user-jwt",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "999",
                        "roles", Set.of("PARENT"),
                        "permissions", Set.of()
                )
        ));
        when(authService.unlockParent("owner-refresh", "secret"))
                .thenReturn(new AuthenticationResult(
                        response,
                        "replacement-refresh"
                ));
        when(cookieService.createCookie("replacement-refresh"))
                .thenReturn(replacementCookie);

        mockMvc.perform(post("/api/v1/auth/parent-session/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer other-user-jwt")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "refreshToken",
                                "owner-refresh"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10L));

        verify(authService).unlockParent("owner-refresh", "secret");
    }
}
