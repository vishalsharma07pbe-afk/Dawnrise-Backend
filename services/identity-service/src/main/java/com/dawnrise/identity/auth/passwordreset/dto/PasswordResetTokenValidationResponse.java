package com.dawnrise.identity.auth.passwordreset.dto;

public class PasswordResetTokenValidationResponse {

    private final boolean valid;

    public PasswordResetTokenValidationResponse(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() {
        return valid;
    }
}
