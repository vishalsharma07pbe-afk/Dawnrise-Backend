package com.dawnrise.identity.auth.session.security;

import com.dawnrise.identity.auth.model.IdentityType;
import com.dawnrise.identity.auth.session.exception.RecentAuthenticationRequiredException;
import com.dawnrise.identity.auth.session.exception.AuthenticatedSessionLockedException;
import com.dawnrise.identity.auth.session.service.AuthenticatedSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

import java.io.IOException;
import java.util.UUID;

public class AuthenticatedSessionFilter extends OncePerRequestFilter {

    private final AuthenticatedSessionService sessions;

    public AuthenticatedSessionFilter(AuthenticatedSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/internal/")
                || path.equals("/api/v1/auth/session/unlock")
                || path.equals("/api/v1/auth/session/status")
                || path.equals("/api/v1/auth/parent-session/unlock")
                || path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/logout")
                || path.equals("/api/v1/platform/auth/login")
                || path.equals("/api/v1/platform/auth/refresh")
                || path.equals("/api/v1/platform/auth/logout")
                || path.equals("/api/v1/platform/auth/session/unlock")
                || path.equals("/api/v1/platform/auth/session/status");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken token)) {
            chain.doFilter(request, response);
            return;
        }

        String identityTypeClaim = token.getToken().getClaimAsString("identityType");
        if ("INTERNAL_SERVICE".equals(identityTypeClaim)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            IdentityType identityType = IdentityType.valueOf(identityTypeClaim);
            UUID sessionId = UUID.fromString(requiredClaim(token, "sessionId"));
            Long ownerId = Long.valueOf(token.getToken().getSubject());
            if (ownerId <= 0) {
                throw new IllegalArgumentException("Invalid subject");
            }
            sessions.validateAndTouch(sessionId, identityType, ownerId);
            if (isSensitive(request)) {
                sessions.requireRecentAuthentication(sessionId, identityType, ownerId);
            }
        } catch (RecentAuthenticationRequiredException exception) {
            reject(response, 403, "RECENT_AUTHENTICATION_REQUIRED", "Recent authentication is required");
            return;
        } catch (AuthenticatedSessionLockedException exception) {
            reject(response, 423, "SESSION_LOCKED", "Workspace is locked");
            return;
        } catch (IllegalArgumentException | NullPointerException exception) {
            reject(response, 401, "INVALID_SESSION_CLAIMS", "Session claims are missing or invalid");
            return;
        } catch (DataAccessException | TransactionException exception) {
            reject(response, 503, "SESSION_VALIDATION_UNAVAILABLE", "Session validation is unavailable");
            return;
        }

        chain.doFilter(request, response);
    }

    private String requiredClaim(JwtAuthenticationToken token, String name) {
        String value = token.getToken().getClaimAsString(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }

    private boolean isSensitive(HttpServletRequest request) {
        String path = request.getRequestURI();
        return (request.getMethod().equals("POST") && path.equals("/api/v1/auth/password/change"))
                || (request.getMethod().equals("PUT")
                    && path.matches("/api/v1/organizations/[^/]+/users/[^/]+/profile"))
                || (request.getMethod().equals("POST")
                    && path.matches("/api/v1/organizations/[^/]+/profile-change-requests/users/[^/]+"));
    }

    private void reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
