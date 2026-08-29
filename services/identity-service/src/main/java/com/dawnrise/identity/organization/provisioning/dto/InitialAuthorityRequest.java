package com.dawnrise.identity.organization.provisioning.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class InitialAuthorityRequest {

    @NotBlank(message = "Authority username is required")
    @Size(
            min = 3,
            max = 100,
            message = "Authority username must contain between 3 and 100 characters"
    )
    @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "Authority username contains invalid characters"
    )
    private String username;

    @NotBlank(message = "Authority first name is required")
    @Size(
            max = 100,
            message = "Authority first name cannot exceed 100 characters"
    )
    private String firstName;

    @Size(
            max = 100,
            message = "Authority middle name cannot exceed 100 characters"
    )
    private String middleName;

    @NotBlank(message = "Authority last name is required")
    @Size(
            max = 100,
            message = "Authority last name cannot exceed 100 characters"
    )
    private String lastName;

    @NotBlank(message = "Authority email is required")
    @Email(message = "Authority email must be valid")
    @Size(
            max = 150,
            message = "Authority email cannot exceed 150 characters"
    )
    private String email;

    @Pattern(
            regexp = "^$|^[0-9+() -]{7,20}$",
            message = "Authority phone number must be valid"
    )
    private String phone;

    public InitialAuthorityRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}