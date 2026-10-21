package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.timetable.controller.TimetableConfigurationController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetableConfigurationAuthorizationContractTest {
    @Test
    void everyEndpointRequiresExplicitTimetablePermissionAndTenantClaim() {
        for (Method method : TimetableConfigurationController.class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.getName().equals("org") || method.getName().equals("actor")) continue;
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertTrue(authorization != null, method.getName() + " must declare @PreAuthorize");
            assertTrue(authorization.value().contains("hasOrganization(authentication)"));
            assertTrue(authorization.value().contains("TIMETABLE_CONFIG_"));
        }
    }
}
