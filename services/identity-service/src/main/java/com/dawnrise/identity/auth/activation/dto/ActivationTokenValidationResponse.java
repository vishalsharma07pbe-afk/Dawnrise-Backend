package com.dawnrise.identity.auth.activation.dto;

public class ActivationTokenValidationResponse {

    private final boolean valid;

    public ActivationTokenValidationResponse(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() {
        return valid;
    }
}