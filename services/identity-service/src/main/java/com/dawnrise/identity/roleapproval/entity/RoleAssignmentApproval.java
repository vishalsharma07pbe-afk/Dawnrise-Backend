package com.dawnrise.identity.roleapproval.entity;

import com.dawnrise.identity.roleapproval.enums.ApprovalDecision;
import com.dawnrise.identity.user.enums.UserRole;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "role_assignment_approvals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_role_approval_request_approver",
                        columnNames = {
                                "request_id",
                                "approver_user_id"
                        }
                )
        }
)
public class RoleAssignmentApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "approver_user_id", nullable = false)
    private Long approverUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false, length = 50)
    private UserRole approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision;

    @Column(length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(name = "decided_at", nullable = false, updatable = false)
    private OffsetDateTime decidedAt;

    public RoleAssignmentApproval() {
    }

    public RoleAssignmentApproval(
            Long requestId,
            Long approverUserId,
            UserRole approverRole,
            ApprovalDecision decision,
            String remarks
    ) {
        this.requestId = requestId;
        this.approverUserId = approverUserId;
        this.approverRole = approverRole;
        this.decision = decision;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getApproverUserId() {
        return approverUserId;
    }

    public UserRole getApproverRole() {
        return approverRole;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public String getRemarks() {
        return remarks;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}