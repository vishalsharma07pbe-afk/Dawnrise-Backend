package com.dawnrise.identity.roleremoval.entity;

import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.user.enums.UserRole;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "role_removal_requests")
public class RoleRemovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 50)
    private UserRole requestedRole;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public RoleRemovalRequest() {
    }

    public RoleRemovalRequest(
            Long organizationId,
            Long userId,
            UserRole requestedRole,
            Long requestedByUserId,
            String reason
    ) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.requestedRole = requestedRole;
        this.requestedByUserId = requestedByUserId;
        this.reason = reason;
        this.status = ApprovalStatus.PENDING;
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public Long getOrganizationId() { return organizationId; }
    public Long getUserId() { return userId; }
    public UserRole getRequestedRole() { return requestedRole; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public String getReason() { return reason; }
    public ApprovalStatus getStatus() { return status; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    public void approve() {
        ensurePending();
        this.status = ApprovalStatus.APPROVED;
        this.completedAt = OffsetDateTime.now();
    }

    public void reject() {
        ensurePending();
        this.status = ApprovalStatus.REJECTED;
        this.completedAt = OffsetDateTime.now();
    }

    public void cancel() {
        ensurePending();
        this.status = ApprovalStatus.CANCELLED;
        this.completedAt = OffsetDateTime.now();
    }

    private void ensurePending() {
        if (!isPending()) {
            throw new IllegalStateException(
                    "Only a pending role removal request can be changed"
            );
        }
    }
}
