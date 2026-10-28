package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.timetable.controller.TimetableDraftController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetableDraftAuthorizationContractTest {
    @Test
    void allDraftEndpointsAreTenantAndPermissionProtected() {
        for (Method method : TimetableDraftController.class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.getName().equals("org") || method.getName().equals("actor")) continue;
            PreAuthorize rule=method.getAnnotation(PreAuthorize.class);
            assertNotNull(rule,method.getName()+" must declare authorization");
            assertTrue(rule.value().contains("hasOrganization(authentication)"));
            assertTrue(rule.value().contains("TIMETABLE_DRAFT_"));
        }
    }
}
