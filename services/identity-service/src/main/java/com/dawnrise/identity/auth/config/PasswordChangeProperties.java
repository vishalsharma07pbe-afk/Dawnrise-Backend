package com.dawnrise.identity.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "security.password-change")
public class PasswordChangeProperties {

    // Applies only to logged-in password changes; reset tokens are emergency recovery.
    private Duration cooldown;

    public Duration getCooldown() {
        return cooldown;
    }

    public void setCooldown(Duration cooldown) {
        this.cooldown = cooldown;
    }
}
