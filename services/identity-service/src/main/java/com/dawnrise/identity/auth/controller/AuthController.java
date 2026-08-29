package com.dawnrise.identity.auth.controller;

import com.dawnrise.identity.auth.dto.ChangePasswordRequest;
import com.dawnrise.identity.auth.dto.LoginRequest;
import com.dawnrise.identity.auth.dto.LoginResponse;
import com.dawnrise.identity.auth.model.AuthenticationResult;
import com.dawnrise.identity.auth.refreshtoken.cookie.RefreshTokenCookieService;
import com.dawnrise.identity.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieService cookieService;

    public AuthController(
            AuthService authService,
            RefreshTokenCookieService cookieService
    ) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        // Login creates a short-lived access token and a refresh cookie.
        AuthenticationResult result =
                authService.login(request);

        ResponseCookie refreshCookie =
                cookieService.createCookie(
                        result.rawRefreshToken()
                );

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie.toString()
                )
                .body(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            HttpServletRequest request
    ) {
        // Refresh tokens live in HttpOnly cookies, not in request bodies.
        String rawRefreshToken =
                cookieService.extractToken(request);

        AuthenticationResult result =
                authService.refresh(rawRefreshToken);

        ResponseCookie replacementCookie =
                cookieService.createCookie(
                        result.rawRefreshToken()
                );

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        replacementCookie.toString()
                )
                .body(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request
    ) {
        // Logout revokes the submitted refresh token and clears the cookie.
        String rawRefreshToken =
                cookieService.extractToken(request);

        authService.logout(rawRefreshToken);

        ResponseCookie clearedCookie =
                cookieService.clearCookie();

        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        clearedCookie.toString()
                )
                .build();
    }

    @PostMapping("/password/change")
    @PreAuthorize("@organizationTokenSecurity.isOrganizationUser(authentication)")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        // The JWT subject is the authenticated user changing their password.
        authService.changePassword(
                Long.valueOf(jwt.getSubject()),
                request
        );

        return ResponseEntity.noContent().build();
    }
}
