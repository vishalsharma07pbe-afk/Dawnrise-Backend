package com.dawnrise.identity.roleapproval.dto;

import java.util.List;

public class RoleAssignmentRequestDetailsResponse {

    private RoleAssignmentRequestResponse request;
    private List<RoleAssignmentApprovalResponse> approvalHistory;

    public RoleAssignmentRequestDetailsResponse() {
    }

    public RoleAssignmentRequestDetailsResponse(
            RoleAssignmentRequestResponse request,
            List<RoleAssignmentApprovalResponse> approvalHistory
    ) {
        this.request = request;
        this.approvalHistory = approvalHistory;
    }

    public RoleAssignmentRequestResponse getRequest() {
        return request;
    }

    public List<RoleAssignmentApprovalResponse> getApprovalHistory() {
        return approvalHistory;
    }
}