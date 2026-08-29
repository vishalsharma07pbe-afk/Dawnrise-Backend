package com.dawnrise.identity.auth.activation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "security.activation-token")
public class ActivationTokenProperties {

    // Values are configured externally to keep resend limits deploy-time adjustable.
    private Duration expiration;
    private Duration resendWindow;
    private Integer maxEmailsPerWindow;

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }

    public Duration getResendWindow() {
        return resendWindow;
    }

    public void setResendWindow(Duration resendWindow) {
        this.resendWindow = resendWindow;
    }

    public int getMaxEmailsPerWindow() {
        return maxEmailsPerWindow;
    }

    public void setMaxEmailsPerWindow(
            Integer maxEmailsPerWindow
    ) {
        this.maxEmailsPerWindow = maxEmailsPerWindow;
    }
}
