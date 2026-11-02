package com.dawnrise.school.leave;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RecentAuthenticationGuard {
    private static final Duration MAX_AGE=Duration.ofMinutes(10);
    public void require(Jwt jwt){
        Instant authenticated=jwt.getClaimAsInstant("auth_time");
        if(authenticated==null||authenticated.plus(MAX_AGE).isBefore(Instant.now()))
            throw new RecentAuthenticationRequiredException();
    }
    public static class RecentAuthenticationRequiredException extends RuntimeException {
        public RecentAuthenticationRequiredException(){super("Recent authentication is required");}
    }
}
