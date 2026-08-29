package com.dawnrise.identity.auth.refreshtoken.config;

import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "security.refresh-token")
public class RefreshTokenProperties {

    // Operational values live in application.yaml so policy changes do not require code changes.
    private Duration expiration;
    private Duration absoluteSessionLifetime;
    private List<UserRole> shortSessionRoles;
    private Duration shortSessionExpiration;
    private Duration shortSessionAbsoluteLifetime;
    private List<UserRole> standardSessionRoles;
    private Duration standardSessionExpiration;
    private Duration standardSessionAbsoluteLifetime;
    private String cookieName;
    private String cookiePath;
    private Boolean secure;
    private String sameSite;

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

    public List<UserRole> getShortSessionRoles() {
        return shortSessionRoles;
    }

    public void setShortSessionRoles(
            List<UserRole> shortSessionRoles
    ) {
        this.shortSessionRoles = shortSessionRoles;
    }

    public Duration getShortSessionExpiration() {
        return shortSessionExpiration;
    }

    public void setShortSessionExpiration(
            Duration shortSessionExpiration
    ) {
        this.shortSessionExpiration = shortSessionExpiration;
    }

    public Duration getShortSessionAbsoluteLifetime() {
        return shortSessionAbsoluteLifetime;
    }

    public void setShortSessionAbsoluteLifetime(
            Duration shortSessionAbsoluteLifetime
    ) {
        this.shortSessionAbsoluteLifetime =
                shortSessionAbsoluteLifetime;
    }

    public List<UserRole> getStandardSessionRoles() {
        return standardSessionRoles;
    }

    public void setStandardSessionRoles(
            List<UserRole> standardSessionRoles
    ) {
        this.standardSessionRoles = standardSessionRoles;
    }

    public Duration getStandardSessionExpiration() {
        return standardSessionExpiration;
    }

    public void setStandardSessionExpiration(
            Duration standardSessionExpiration
    ) {
        this.standardSessionExpiration = standardSessionExpiration;
    }

    public Duration getStandardSessionAbsoluteLifetime() {
        return standardSessionAbsoluteLifetime;
    }

    public void setStandardSessionAbsoluteLifetime(
            Duration standardSessionAbsoluteLifetime
    ) {
        this.standardSessionAbsoluteLifetime =
                standardSessionAbsoluteLifetime;
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

    public void setSecure(Boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }
}
