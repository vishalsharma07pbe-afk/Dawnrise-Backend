package com.dawnrise.identity.notification.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "dawnrise.notification-sms")
public class NotificationSmsProperties {
    private boolean enabled;
    private String baseUrl = "https://control.msg91.com";
    private String authKey;
    private Duration delay = Duration.ofSeconds(30);
    private Duration initialDelay = Duration.ofSeconds(30);
    private int maximumAttempts = 5;
    private Map<String, String> templateIds = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthKey() { return authKey; }
    public void setAuthKey(String authKey) { this.authKey = authKey; }
    public Duration getDelay() { return delay; }
    public void setDelay(Duration delay) { this.delay = delay; }
    public Duration getInitialDelay() { return initialDelay; }
    public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }
    public int getMaximumAttempts() { return maximumAttempts; }
    public void setMaximumAttempts(int maximumAttempts) { this.maximumAttempts = maximumAttempts; }
    public Map<String, String> getTemplateIds() { return templateIds; }
    public void setTemplateIds(Map<String, String> templateIds) { this.templateIds = templateIds; }

    public String templateId(String eventType) { return templateIds.get(eventType); }
    public boolean configured() {
        return enabled && authKey != null && !authKey.isBlank()
                && templateIds.values().stream().anyMatch(value -> value != null && !value.isBlank());
    }
}
