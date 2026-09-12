package com.dawnrise.school.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "security.internal")
public class InternalServiceSecurityProperties
        implements InitializingBean {

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

    @Override
    public void afterPropertiesSet() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "security.internal.api-key must be configured"
            );
        }

        boolean hasAllowedServiceName = allowedServiceNames != null
                && allowedServiceNames.stream()
                .anyMatch(serviceName ->
                        serviceName != null
                                && !serviceName.isBlank()
                );

        if (!hasAllowedServiceName) {
            throw new IllegalStateException(
                    "security.internal.allowed-service-names must include at least one service"
            );
        }
    }
}
