package com.dawnrise.identity.roleremoval.dto;

import java.util.List;

public class RoleRemovalRequestDetailsResponse {

    private RoleRemovalRequestResponse request;
    private List<RoleRemovalApprovalResponse> approvalHistory;

    public RoleRemovalRequestDetailsResponse() {
    }

    public RoleRemovalRequestDetailsResponse(
            RoleRemovalRequestResponse request,
            List<RoleRemovalApprovalResponse> approvalHistory
    ) {
        this.request = request;
        this.approvalHistory = approvalHistory;
    }

    public RoleRemovalRequestResponse getRequest() { return request; }
    public List<RoleRemovalApprovalResponse> getApprovalHistory() { return approvalHistory; }
}
