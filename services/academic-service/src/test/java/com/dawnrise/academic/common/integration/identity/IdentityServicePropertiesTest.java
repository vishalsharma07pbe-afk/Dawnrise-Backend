package com.dawnrise.academic.common.integration.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityServicePropertiesTest {

    @Test
    void defaultsAreFailClosedAndLocal() {
        IdentityServiceProperties properties =
                new IdentityServiceProperties();

        assertThat(properties.getBaseUrl())
                .isEqualTo("http://localhost:8081");
        assertThat(properties.getConnectTimeout())
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getReadTimeout())
                .isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getApiKey()).isNull();
        assertThat(properties.getServiceName())
                .isEqualTo("academic-service");
    }

    @Test
    void configuredValuesAreRetained() {
        IdentityServiceProperties properties =
                new IdentityServiceProperties();

        properties.setBaseUrl("http://identity.internal");
        properties.setConnectTimeout(Duration.ofMillis(500));
        properties.setReadTimeout(Duration.ofSeconds(1));
        properties.setApiKey("test-key");
        properties.setServiceName("academic-service");

        assertThat(properties.getBaseUrl())
                .isEqualTo("http://identity.internal");
        assertThat(properties.getConnectTimeout())
                .isEqualTo(Duration.ofMillis(500));
        assertThat(properties.getReadTimeout())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getApiKey()).isEqualTo("test-key");
        assertThat(properties.getServiceName())
                .isEqualTo("academic-service");
    }
}
