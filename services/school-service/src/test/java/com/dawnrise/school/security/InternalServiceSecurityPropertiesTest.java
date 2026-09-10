package com.dawnrise.school.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalServiceSecurityPropertiesTest {

    @Test
    void afterPropertiesSet_whenConfigurationIsValid_succeeds() {
        InternalServiceSecurityProperties properties =
                new InternalServiceSecurityProperties();
        properties.setApiKey("test-internal-key");
        properties.setAllowedServiceNames(
                List.of("academic-service")
        );

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void afterPropertiesSet_whenApiKeyIsMissing_failsClearly() {
        InternalServiceSecurityProperties properties =
                new InternalServiceSecurityProperties();
        properties.setAllowedServiceNames(
                List.of("academic-service")
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                properties::afterPropertiesSet
        );

        assertEquals(
                "security.internal.api-key must be configured",
                exception.getMessage()
        );
    }

    @Test
    void afterPropertiesSet_whenApiKeyIsBlank_failsClearly() {
        InternalServiceSecurityProperties properties =
                new InternalServiceSecurityProperties();
        properties.setApiKey(" ");
        properties.setAllowedServiceNames(
                List.of("academic-service")
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                properties::afterPropertiesSet
        );

        assertEquals(
                "security.internal.api-key must be configured",
                exception.getMessage()
        );
    }
}
