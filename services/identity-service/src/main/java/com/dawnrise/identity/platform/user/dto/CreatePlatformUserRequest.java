package com.dawnrise.identity.platform.user.dto;

import com.dawnrise.identity.platform.user.enums.PlatformRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;

public class CreatePlatformUserRequest {

    @NotBlank(message = "Username is required")
    @Size(
            min = 3,
            max = 100,
            message = "Username must contain between 3 and 100 characters"
    )
    @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "Username contains invalid characters"
    )
    private String username;

    @NotBlank(message = "First name is required")
    @Size(
            max = 100,
            message = "First name cannot exceed 100 characters"
    )
    private String firstName;

    @Size(
            max = 100,
            message = "Middle name cannot exceed 100 characters"
    )
    private String middleName;

    @Size(
            max = 100,
            message = "Last name cannot exceed 100 characters"
    )
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(
            max = 150,
            message = "Email cannot exceed 150 characters"
    )
    private String email;

    @Pattern(
            regexp = "^[0-9+() -]{7,20}$",
            message = "Phone number must be valid"
    )
    private String phone;

    @NotEmpty(message = "At least one platform role is required")
    private Set<PlatformRole> roles = new HashSet<>();

    public CreatePlatformUserRequest() {
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

    public Set<PlatformRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<PlatformRole> roles) {
        this.roles = roles == null
                ? new HashSet<>()
                : new HashSet<>(roles);
    }
}