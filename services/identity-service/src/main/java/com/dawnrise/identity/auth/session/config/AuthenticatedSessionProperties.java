package com.dawnrise.identity.auth.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "dawnrise.auth.session")
public class AuthenticatedSessionProperties {

    private Duration inactivityTimeout = Duration.ofMinutes(15);
    private Duration recentAuthentication = Duration.ofMinutes(10);

    public Duration getInactivityTimeout() {
        return inactivityTimeout;
    }

    public void setInactivityTimeout(Duration inactivityTimeout) {
        this.inactivityTimeout = inactivityTimeout;
    }

    public Duration getRecentAuthentication() {
        return recentAuthentication;
    }

    public void setRecentAuthentication(Duration recentAuthentication) {
        this.recentAuthentication = recentAuthentication;
    }
}
