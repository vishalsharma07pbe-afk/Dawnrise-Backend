package com.edusphere.identity.platform.auth.refreshtoken.cookie;

import com.edusphere.identity.platform.auth.refreshtoken.config.PlatformRefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PlatformRefreshTokenCookieService {

    private final PlatformRefreshTokenProperties properties;

    public PlatformRefreshTokenCookieService(
            PlatformRefreshTokenProperties properties
    ) {
        this.properties = properties;
    }

    public ResponseCookie createCookie(String rawRefreshToken) {
        return ResponseCookie
                .from(properties.getCookieName(), rawRefreshToken)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(properties.getCookiePath())
                .maxAge(properties.getExpiration())
                .build();
    }

    public ResponseCookie clearCookie() {
        return ResponseCookie
                .from(properties.getCookieName(), "")
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(properties.getCookiePath())
                .maxAge(Duration.ZERO)
                .build();
    }

    public String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (properties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
