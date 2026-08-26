package com.edusphere.identity.organization.provisioning.exception;

public class ProvisioningConflictException
        extends RuntimeException {

    private final String field;

    public ProvisioningConflictException(
            String message
    ) {
        this(null, message);
    }

    public ProvisioningConflictException(
            String field,
            String message
    ) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }

    public boolean hasField() {
        return field != null && !field.isBlank();
    }
}