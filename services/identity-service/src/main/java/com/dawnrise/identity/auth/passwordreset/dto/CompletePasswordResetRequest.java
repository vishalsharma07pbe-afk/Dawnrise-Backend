package com.dawnrise.identity.auth.passwordreset.dto;

import com.dawnrise.identity.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompletePasswordResetRequest {

    @NotBlank(message = "Password reset token is required")
    @Size(max = 500, message = "Password reset token is invalid")
    private String token;

    @NotBlank(message = "Password is required")
    @StrongPassword
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;

    public CompletePasswordResetRequest() {
    }

    public CompletePasswordResetRequest(
            String token,
            String password,
            String confirmPassword
    ) {
        this.token = token;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
