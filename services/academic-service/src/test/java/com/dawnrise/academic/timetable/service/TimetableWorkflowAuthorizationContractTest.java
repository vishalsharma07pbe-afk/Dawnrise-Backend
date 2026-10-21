package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.timetable.controller.TimetableWorkflowController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetableWorkflowAuthorizationContractTest {
    @Test
    void currentSchoolDateIsTenantScopedAndAvailableToPublishedViewPermissions() throws Exception {
        Method endpoint=TimetableWorkflowController.class.getDeclaredMethod(
                "currentDate",
                org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken.class);
        PreAuthorize rule=endpoint.getAnnotation(PreAuthorize.class);
        assertNotNull(rule);
        assertTrue(rule.value().contains("hasOrganization(authentication)"));
        assertTrue(rule.value().contains("TIMETABLE_PUBLISHED_VIEW"));
        assertTrue(rule.value().contains("TIMETABLE_PUBLISHED_VIEW_SELF"));
    }
}
