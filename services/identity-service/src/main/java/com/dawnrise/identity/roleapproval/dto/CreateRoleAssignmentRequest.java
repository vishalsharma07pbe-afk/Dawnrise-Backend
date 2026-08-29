package com.dawnrise.identity.roleapproval.dto;

import com.dawnrise.identity.user.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateRoleAssignmentRequest {

    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

    @NotNull(message = "Requested role is required")
    private UserRole requestedRole;

    @NotBlank(message = "Reason is required")
    @Size(
            max = 500,
            message = "Reason cannot exceed 500 characters"
    )
    private String reason;

    public CreateRoleAssignmentRequest() {
    }

    public CreateRoleAssignmentRequest(
            Long userId,
            UserRole requestedRole,
            String reason
    ) {
        this.userId = userId;
        this.requestedRole = requestedRole;
        this.reason = reason;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UserRole getRequestedRole() {
        return requestedRole;
    }

    public void setRequestedRole(UserRole requestedRole) {
        this.requestedRole = requestedRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}