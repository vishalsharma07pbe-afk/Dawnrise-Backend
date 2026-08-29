package com.dawnrise.school.school.provisioning;

import java.util.Map;

public class IdentityProvisioningException
        extends RuntimeException {

    private final int statusCode;
    private final Map<String, String> fieldErrors;

    public IdentityProvisioningException(
            int statusCode,
            String message,
            Map<String, String> fieldErrors
    ) {
        super(message);
        this.statusCode = statusCode;
        this.fieldErrors = fieldErrors == null
                ? Map.of()
                : Map.copyOf(fieldErrors);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}