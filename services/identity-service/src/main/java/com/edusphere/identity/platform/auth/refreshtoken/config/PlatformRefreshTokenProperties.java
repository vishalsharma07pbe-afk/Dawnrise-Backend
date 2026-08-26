package com.edusphere.identity.platform.auth.refreshtoken.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.auth.refresh-token")
public class PlatformRefreshTokenProperties {

    private Duration expiration = Duration.ofDays(7);
    private Duration absoluteSessionLifetime = Duration.ofDays(30);
    private String cookieName = "dawnrise_platform_refresh";
    private String cookiePath = "/api/v1/platform/auth";
    private boolean secure = false;
    private String sameSite = "Strict";

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }

    public Duration getAbsoluteSessionLifetime() {
        return absoluteSessionLifetime;
    }

    public void setAbsoluteSessionLifetime(
            Duration absoluteSessionLifetime
    ) {
        this.absoluteSessionLifetime = absoluteSessionLifetime;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }
}
