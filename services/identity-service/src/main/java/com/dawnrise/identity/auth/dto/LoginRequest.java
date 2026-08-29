package com.dawnrise.identity.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "School code is required")
    @Size(max = 50, message = "School code is invalid")
    private String schoolCode;

    @NotBlank(message = "Username or email is required")
    @Size(max = 150, message = "Username or email is invalid")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    @Size(max = 72, message = "Invalid login credentials")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(
            String schoolCode,
            String usernameOrEmail,
            String password
    ) {
        this.schoolCode = schoolCode;
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    public void setSchoolCode(String schoolCode) {
        this.schoolCode = schoolCode;
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
