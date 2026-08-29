package com.dawnrise.identity.organization.provisioning.exception;

import com.dawnrise.identity.common.exception.ApiErrorResponse;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProvisioningConflictExceptionTest {

    @Test
    void messageOnlyConstructor_preservesCompatibility() {
        ProvisioningConflictException exception =
                new ProvisioningConflictException("Provisioning conflict");

        assertEquals("Provisioning conflict", exception.getMessage());
        assertNull(exception.getField());
        assertFalse(exception.hasField());
    }

    @Test
    void fieldConflictHandler_returnsGenericMessageAndValidationErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/internal/v1/test");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleProvisioningConflict(
                        new ProvisioningConflictException(
                                "authority.email",
                                "This email address is already registered."
                        ),
                        request
                );

        assertEquals(409, response.getStatusCode().value());
        assertEquals(
                "Provisioning request conflicts with existing data",
                response.getBody().getMessage()
        );
        assertEquals(
                "This email address is already registered.",
                response.getBody()
                        .getValidationErrors()
                        .get("authority.email")
        );
    }

    @Test
    void nonFieldConflictHandler_preservesMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/internal/v1/test");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleProvisioningConflict(
                        new ProvisioningConflictException(
                                "Provisioning request is already in progress"
                        ),
                        request
                );

        assertEquals(409, response.getStatusCode().value());
        assertEquals(
                "Provisioning request is already in progress",
                response.getBody().getMessage()
        );
        assertNull(response.getBody().getValidationErrors());
    }

    @Test
    void fieldConstructor_detectsNonBlankField() {
        ProvisioningConflictException exception =
                new ProvisioningConflictException(
                        "authority.username",
                        "This username is already registered."
                );

        assertTrue(exception.hasField());
        assertEquals("authority.username", exception.getField());
    }
}
