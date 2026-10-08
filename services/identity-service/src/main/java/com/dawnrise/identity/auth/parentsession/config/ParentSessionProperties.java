package com.dawnrise.identity.auth.parentsession.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
@Component
@ConfigurationProperties(prefix = "security.parent-session")
public class ParentSessionProperties {
    private Duration inactivityTimeout = Duration.ofMinutes(15);
    private Duration recentAuthentication = Duration.ofMinutes(10);
    public Duration getInactivityTimeout() { return inactivityTimeout; }
    public void setInactivityTimeout(Duration value) { inactivityTimeout = value; }
    public Duration getRecentAuthentication() { return recentAuthentication; }
    public void setRecentAuthentication(Duration value) { recentAuthentication = value; }
}
