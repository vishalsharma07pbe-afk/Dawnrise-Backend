package com.dawnrise.identity.platform.auth.passwordreset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PlatformPasswordResetRequest {
    @NotBlank(message = "Username is required")
    @Size(max = 100, message = "Username is invalid")
    private String username;
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
