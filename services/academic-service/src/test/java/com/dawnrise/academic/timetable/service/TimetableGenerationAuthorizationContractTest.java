package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.timetable.controller.TimetableGenerationController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetableGenerationAuthorizationContractTest {
    @Test
    void everyGenerationEndpointIsTenantAndPermissionProtected(){
        for(Method method:TimetableGenerationController.class.getDeclaredMethods()){
            if(method.isSynthetic()||method.getName().equals("org")||method.getName().equals("actor"))continue;
            PreAuthorize rule=method.getAnnotation(PreAuthorize.class);assertNotNull(rule);
            assertTrue(rule.value().contains("hasOrganization(authentication)"));
            assertTrue(rule.value().contains("TIMETABLE_"));
        }
    }
}
