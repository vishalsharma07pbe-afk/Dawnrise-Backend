package com.edusphere.identity.auth.controller;

import com.edusphere.identity.auth.refreshtoken.cookie.RefreshTokenCookieService;
import com.edusphere.identity.auth.service.AuthService;
import com.edusphere.identity.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenCookieService cookieService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(jwtPrincipal("10"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_whenRequiredFieldsMissing_returnsValidationErrors()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolCode": "",
                                  "usernameOrEmail": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.schoolCode")
                        .value("School code is required"))
                .andExpect(jsonPath("$.validationErrors.usernameOrEmail")
                        .value("Username or email is required"))
                .andExpect(jsonPath("$.validationErrors.password")
                        .value("Password is required"));

        verify(authService, never()).login(any());
    }

    @Test
    void changePassword_whenValid_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "Teacher@123",
                                  "newPassword": "N3w-Teacher!",
                                  "confirmPassword": "N3w-Teacher!"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(eq(10L), any());
    }

    @Test
    void changePassword_whenInvalid_returnsValidationErrors()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "",
                                  "newPassword": "weak",
                                  "confirmPassword": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.currentPassword")
                        .value("Current password is required"))
                .andExpect(jsonPath("$.validationErrors.confirmPassword")
                        .value("Password confirmation is required"));

        verify(authService, never()).changePassword(any(), any());
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
