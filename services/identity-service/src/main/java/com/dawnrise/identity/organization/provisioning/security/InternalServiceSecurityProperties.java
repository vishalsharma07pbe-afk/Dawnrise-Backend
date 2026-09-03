package com.dawnrise.identity.organization.provisioning.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "security.internal")
public class InternalServiceSecurityProperties {

    private String apiKey;
    private List<String> allowedServiceNames = new ArrayList<>();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<String> getAllowedServiceNames() {
        return allowedServiceNames;
    }

    public void setAllowedServiceNames(
            List<String> allowedServiceNames
    ) {
        this.allowedServiceNames = allowedServiceNames;
    }
}