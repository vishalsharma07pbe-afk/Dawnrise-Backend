package com.dawnrise.school;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchoolSchedulingConfigurationTest {
    @Test
    void scheduledOutboxWorkersAreEnabled(){
        assertTrue(SchoolServiceApplication.class.isAnnotationPresent(EnableScheduling.class));
    }
}
