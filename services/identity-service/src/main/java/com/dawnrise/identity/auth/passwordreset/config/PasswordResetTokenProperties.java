package com.dawnrise.identity.auth.passwordreset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "security.password-reset-token")
public class PasswordResetTokenProperties {

    // Email request throttling limits abuse while valid reset tokens stay usable.
    private Duration expiration;
    private Duration requestWindow;
    private Integer maxEmailsPerWindow;

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }

    public Duration getRequestWindow() {
        return requestWindow;
    }

    public void setRequestWindow(Duration requestWindow) {
        this.requestWindow = requestWindow;
    }

    public int getMaxEmailsPerWindow() {
        return maxEmailsPerWindow;
    }

    public void setMaxEmailsPerWindow(Integer maxEmailsPerWindow) {
        this.maxEmailsPerWindow = maxEmailsPerWindow;
    }

}
