package com.dawnrise.identity.auth.parentsession.controller;

import com.dawnrise.identity.auth.dto.LoginResponse;
import com.dawnrise.identity.auth.model.AuthenticationResult;
import com.dawnrise.identity.auth.parentsession.dto.ParentSessionValidationResponse;
import com.dawnrise.identity.auth.parentsession.dto.UnlockParentSessionRequest;
import com.dawnrise.identity.auth.parentsession.service.ParentSessionService;
import com.dawnrise.identity.auth.refreshtoken.cookie.RefreshTokenCookieService;
import com.dawnrise.identity.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ParentSessionController {

    private final ParentSessionService sessions;
    private final AuthService auth;
    private final RefreshTokenCookieService cookies;

    public ParentSessionController(
            ParentSessionService sessions,
            AuthService auth,
            RefreshTokenCookieService cookies
    ) {
        this.sessions = sessions;
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/internal/v1/parent-sessions/{sessionId}/activity")
    public ParentSessionValidationResponse validate(
            @PathVariable UUID sessionId,
            @RequestParam Long userId
    ) {
        return sessions.validateAndTouch(sessionId, userId);
    }

    @PostMapping("/api/v1/auth/parent-session/unlock")
    public ResponseEntity<LoginResponse> unlock(
            HttpServletRequest servletRequest,
            @Valid @RequestBody UnlockParentSessionRequest request
    ) {
        AuthenticationResult result = auth.unlockParent(
                cookies.extractToken(servletRequest),
                request.password()
        );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.createCookie(result.rawRefreshToken()).toString()
                )
                .body(result.response());
    }

    @PostMapping("/api/v1/auth/parent-session/activity")
    @PreAuthorize("hasRole('PARENT') and @organizationTokenSecurity.isOrganizationUser(authentication)")
    public ResponseEntity<Void> activity() {
        return ResponseEntity.noContent().build();
    }
}
