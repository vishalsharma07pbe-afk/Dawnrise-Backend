package com.dawnrise.identity.user.dto;

import com.dawnrise.identity.user.enums.UserRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

public class UpdateUserRolesRequest {

    @NotEmpty(message = "At least one role is required")
    private Set<@NotNull(message = "Role cannot be null") UserRole> roles;

    public UpdateUserRolesRequest() {
    }

    public UpdateUserRolesRequest(Set<UserRole> roles) {
        setRoles(roles);
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