package com.dawnrise.identity.roleremoval.dto;

import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.user.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.Set;

public class RoleRemovalRequestResponse {

    private Long id;
    private Long organizationId;
    private Long userId;
    private UserRole requestedRole;
    private Long requestedByUserId;
    private String reason;
    private ApprovalStatus status;
    private Set<UserRole> approvalsCollected;
    private Set<UserRole> approvalsStillRequired;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public RoleRemovalRequestResponse() {
    }

    public RoleRemovalRequestResponse(
            Long id,
            Long organizationId,
            Long userId,
            UserRole requestedRole,
            Long requestedByUserId,
            String reason,
            ApprovalStatus status,
            Set<UserRole> approvalsCollected,
            Set<UserRole> approvalsStillRequired,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.requestedRole = requestedRole;
        this.requestedByUserId = requestedByUserId;
        this.reason = reason;
        this.status = status;
        this.approvalsCollected = approvalsCollected;
        this.approvalsStillRequired = approvalsStillRequired;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getUserId() { return userId; }
    public UserRole getRequestedRole() { return requestedRole; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public String getReason() { return reason; }
    public ApprovalStatus getStatus() { return status; }
    public Set<UserRole> getApprovalsCollected() { return approvalsCollected; }
    public Set<UserRole> getApprovalsStillRequired() { return approvalsStillRequired; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
