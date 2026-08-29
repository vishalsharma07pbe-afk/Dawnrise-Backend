package com.dawnrise.identity.roleapproval.dto;

import com.dawnrise.identity.roleapproval.enums.ApprovalDecision;
import com.dawnrise.identity.user.enums.UserRole;

import java.time.OffsetDateTime;

public class RoleAssignmentApprovalResponse {

    private Long id;
    private Long requestId;
    private Long approverUserId;
    private UserRole approverRole;
    private ApprovalDecision decision;
    private String remarks;
    private OffsetDateTime decidedAt;

    public RoleAssignmentApprovalResponse() {
    }

    public RoleAssignmentApprovalResponse(
            Long id,
            Long requestId,
            Long approverUserId,
            UserRole approverRole,
            ApprovalDecision decision,
            String remarks,
            OffsetDateTime decidedAt
    ) {
        this.id = id;
        this.requestId = requestId;
        this.approverUserId = approverUserId;
        this.approverRole = approverRole;
        this.decision = decision;
        this.remarks = remarks;
        this.decidedAt = decidedAt;
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