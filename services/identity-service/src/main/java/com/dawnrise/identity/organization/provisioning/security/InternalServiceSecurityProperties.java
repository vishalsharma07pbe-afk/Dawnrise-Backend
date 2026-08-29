package com.dawnrise.identity.organization.provisioning.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.internal")
public class InternalServiceSecurityProperties {

    private String apiKey;
    private String allowedServiceName;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAllowedServiceName() {
        return allowedServiceName;
    }

    public void setAllowedServiceName(
            String allowedServiceName
    ) {
        this.allowedServiceName = allowedServiceName;
    }
}
