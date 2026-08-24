package com.edusphere.identity.platform.auth.controller;

import com.edusphere.identity.platform.auth.dto.PlatformLoginRequest;
import com.edusphere.identity.platform.auth.dto.PlatformLoginResponse;
import com.edusphere.identity.platform.auth.model.PlatformAuthenticationResult;
import com.edusphere.identity.platform.auth.refreshtoken.cookie.PlatformRefreshTokenCookieService;
import com.edusphere.identity.platform.auth.service.PlatformAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/auth")
public class PlatformAuthController {

    private final PlatformAuthService authService;
    private final PlatformRefreshTokenCookieService cookieService;

    public PlatformAuthController(
            PlatformAuthService authService,
            PlatformRefreshTokenCookieService cookieService
    ) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<PlatformLoginResponse> login(
            @Valid @RequestBody PlatformLoginRequest request
    ) {
        PlatformAuthenticationResult result =
                authService.login(request);

        ResponseCookie refreshCookie =
                cookieService.createCookie(
                        result.rawRefreshToken()
                );

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<PlatformLoginResponse> refresh(
            HttpServletRequest request
    ) {
        PlatformAuthenticationResult result =
                authService.refresh(
                        cookieService.extractToken(request)
                );

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
        authService.logout(cookieService.extractToken(request));

        ResponseCookie clearedCookie = cookieService.clearCookie();

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie.toString())
                .build();
    }
}
