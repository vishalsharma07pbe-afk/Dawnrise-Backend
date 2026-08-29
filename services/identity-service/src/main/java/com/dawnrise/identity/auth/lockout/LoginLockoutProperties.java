package com.dawnrise.identity.auth.lockout;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "security.login-lockout")
public class LoginLockoutProperties {

    // Values are configured externally so lockout rules stay operationally adjustable.
    private Integer firstFailureThreshold;
    private Integer escalatedFailureThreshold;
    private Duration firstLockDuration;
    private Duration secondLockDuration;
    private Duration finalLockDuration;
    private String lockedMessagePrefix;
    private String lockedMessageSuffix;

    public int getFirstFailureThreshold() {
        return firstFailureThreshold;
    }

    public void setFirstFailureThreshold(Integer firstFailureThreshold) {
        this.firstFailureThreshold = firstFailureThreshold;
    }

    public int getEscalatedFailureThreshold() {
        return escalatedFailureThreshold;
    }

    public void setEscalatedFailureThreshold(
            Integer escalatedFailureThreshold
    ) {
        this.escalatedFailureThreshold = escalatedFailureThreshold;
    }

    public Duration getFirstLockDuration() {
        return firstLockDuration;
    }

    public void setFirstLockDuration(Duration firstLockDuration) {
        this.firstLockDuration = firstLockDuration;
    }

    public Duration getSecondLockDuration() {
        return secondLockDuration;
    }

    public void setSecondLockDuration(Duration secondLockDuration) {
        this.secondLockDuration = secondLockDuration;
    }

    public Duration getFinalLockDuration() {
        return finalLockDuration;
    }

    public void setFinalLockDuration(Duration finalLockDuration) {
        this.finalLockDuration = finalLockDuration;
    }

    public String getLockedMessagePrefix() {
        return lockedMessagePrefix;
    }

    public void setLockedMessagePrefix(String lockedMessagePrefix) {
        this.lockedMessagePrefix = lockedMessagePrefix;
    }

    public String getLockedMessageSuffix() {
        return lockedMessageSuffix;
    }

    public void setLockedMessageSuffix(String lockedMessageSuffix) {
        this.lockedMessageSuffix = lockedMessageSuffix;
    }
}
