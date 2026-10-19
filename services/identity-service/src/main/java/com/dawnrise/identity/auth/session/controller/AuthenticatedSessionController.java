package com.dawnrise.identity.auth.session.controller;

import com.dawnrise.identity.auth.dto.LoginResponse;
import com.dawnrise.identity.auth.model.AuthenticationResult;
import com.dawnrise.identity.auth.model.IdentityType;
import com.dawnrise.identity.auth.refreshtoken.cookie.RefreshTokenCookieService;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenSession;
import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.auth.service.AuthService;
import com.dawnrise.identity.auth.session.dto.AuthenticatedSessionValidationResponse;
import com.dawnrise.identity.auth.session.dto.SessionBootstrapResponse;
import com.dawnrise.identity.auth.session.dto.UnlockSessionRequest;
import com.dawnrise.identity.auth.session.exception.SessionValidationUnavailableException;
import com.dawnrise.identity.auth.session.service.AuthenticatedSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@RestController
@Validated
public class AuthenticatedSessionController {

    private final AuthenticatedSessionService sessions;
    private final RefreshTokenService refreshTokens;
    private final AuthService auth;
    private final RefreshTokenCookieService cookies;

    public AuthenticatedSessionController(
            AuthenticatedSessionService sessions,
            RefreshTokenService refreshTokens,
            AuthService auth,
            RefreshTokenCookieService cookies
    ) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/internal/v1/authenticated-sessions/{sessionId}/activity")
    public AuthenticatedSessionValidationResponse validate(
            @PathVariable UUID sessionId,
            @RequestParam IdentityType identityType,
            @RequestParam @Positive Long ownerId
    ) {
        return sessions.validateAndTouch(sessionId, identityType, ownerId);
    }

    @PostMapping("/internal/v1/authenticated-sessions/{sessionId}/recent-authentication")
    public ResponseEntity<Void> requireRecentAuthentication(
            @PathVariable UUID sessionId,
            @RequestParam IdentityType identityType,
            @RequestParam @Positive Long ownerId
    ) {
        try {
            sessions.requireRecentAuthentication(sessionId, identityType, ownerId);
            return ResponseEntity.noContent().build();
        } catch (DataAccessException | TransactionException exception) {
            throw new SessionValidationUnavailableException(
                    "Session validation is unavailable",
                    exception
            );
        }
    }

    @PostMapping("/api/v1/auth/session/lock")
    public ResponseEntity<Void> lock(@AuthenticationPrincipal Jwt jwt) {
        sessions.lock(
                UUID.fromString(jwt.getClaimAsString("sessionId")),
                IdentityType.valueOf(jwt.getClaimAsString("identityType")),
                Long.valueOf(jwt.getSubject())
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/auth/session/activity")
    public ResponseEntity<Void> activity() {
        // The authenticated-session filter validates and touches exactly once.
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/auth/session/status")
    public SessionBootstrapResponse status(HttpServletRequest request) {
        try {
            RefreshTokenSession refreshSession = refreshTokens.resolveRefreshTokenSession(
                    cookies.extractToken(request)
            );
            boolean locked = sessions.isLocked(
                    refreshSession.tokenFamilyId(),
                    IdentityType.ORGANIZATION_USER,
                    refreshSession.userId()
            );
            return new SessionBootstrapResponse(locked ? "LOCKED" : "ACTIVE");
        } catch (DataAccessException | TransactionException exception) {
            throw new SessionValidationUnavailableException(
                    "Session validation is unavailable",
                    exception
            );
        }
    }

    @PostMapping("/api/v1/auth/session/unlock")
    public ResponseEntity<LoginResponse> unlock(
            HttpServletRequest servletRequest,
            @Valid @RequestBody UnlockSessionRequest request
    ) {
        AuthenticationResult result = auth.unlockSession(
                cookies.extractToken(servletRequest),
                request.password()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.createCookie(result.rawRefreshToken()).toString())
                .body(result.response());
    }
}
