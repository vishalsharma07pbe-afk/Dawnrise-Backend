package com.dawnrise.identity.user.dto;

import com.dawnrise.identity.user.enums.UserRole;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.HashSet;

public class CreateUserRequest {

    @NotNull(message = "Organization ID is required")
    @Positive(message = "Organization ID must be positive")
    private Long organizationId;

    @NotBlank(message = "Username is required")
    @Size(
            min = 3,
            max = 100,
            message = "Username must be between 3 and 100 characters"
    )
    @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "Username can contain only letters, numbers, dots, underscores, and hyphens"
    )
    private String username;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Middle name cannot exceed 100 characters")
    private String middleName;

    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    @Pattern(
            regexp = "^[0-9+() -]{7,20}$",
            message = "Phone number must be valid"
    )
    private String phone;

    @NotEmpty(message = "At least one role is required")
    private Set<@NotNull(message = "Role cannot be null") UserRole> roles;

    public CreateUserRequest() {
    }

    public CreateUserRequest(
            Long organizationId,
            String username,
            String firstName,
            String middleName,
            String lastName,
            String email,
            String phone,
            Set<UserRole> roles
    ) {
        this.organizationId = organizationId;
        this.username = username;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        setRoles(roles);
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
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

    public Set<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRole> roles) {
        this.roles = roles == null
                ? new HashSet<>()
                : new HashSet<>(roles);
    }
}