package com.edusphere.school.school.exception;

import java.util.Map;

public class FieldValidationException extends RuntimeException {

    private final Map<String, String> validationErrors;

    public FieldValidationException(
            String message,
            Map<String, String> validationErrors
    ) {
        super(message);
        this.validationErrors = validationErrors == null
                ? Map.of()
                : Map.copyOf(validationErrors);
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }
}
