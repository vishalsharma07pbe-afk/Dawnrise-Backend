package com.dawnrise.identity.roleapproval.dto;

import com.dawnrise.identity.roleapproval.enums.ApprovalDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RoleApprovalDecisionRequest {

    @NotNull(message = "Approval decision is required")
    private ApprovalDecision decision;

    @Size(
            max = 500,
            message = "Remarks cannot exceed 500 characters"
    )
    private String remarks;

    public RoleApprovalDecisionRequest() {
    }

    public RoleApprovalDecisionRequest(
            ApprovalDecision decision,
            String remarks
    ) {
        this.decision = decision;
        this.remarks = remarks;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public void setDecision(ApprovalDecision decision) {
        this.decision = decision;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}