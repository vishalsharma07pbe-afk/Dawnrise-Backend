package com.dawnrise.identity.auth.activation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResendActivationRequest {

    @NotBlank(message = "Activation token is required")
    @Size(
            max = 500,
            message = "Activation token is invalid"
    )
    private String token;

    public ResendActivationRequest() {
    }

    public ResendActivationRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}