package com.dawnrise.school.announcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationRelayPropertiesTest {
    @Test
    void academicCredentialIsRequiredBeforeServingLeavePreviews() {
        NotificationRelayProperties properties = new NotificationRelayProperties();
        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
        properties.setApiKey(" ");
        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
        properties.setApiKey("configured-secret");
        assertDoesNotThrow(properties::afterPropertiesSet);
    }
}
