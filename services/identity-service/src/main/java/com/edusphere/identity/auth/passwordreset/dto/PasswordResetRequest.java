package com.edusphere.identity.auth.passwordreset.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetRequest {

    @NotBlank(message = "School code is required")
    @Size(
            max = 50,
            message = "School code is invalid"
    )
    private String schoolCode;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(
            max = 150,
            message = "Email cannot exceed 150 characters"
    )
    private String email;

    public PasswordResetRequest() {
    }

    public PasswordResetRequest(
            String schoolCode,
            String email
    ) {
        this.schoolCode = schoolCode;
        this.email = email;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    public void setSchoolCode(String schoolCode) {
        this.schoolCode = schoolCode;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}