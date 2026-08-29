package com.dawnrise.identity.roleapproval.mapper;

import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentApprovalResponse;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentApproval;
import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.stereotype.Component;

@Component
public class RoleAssignmentApprovalMapper {

    public RoleAssignmentApproval toEntity(
            Long requestId,
            Long approverUserId,
            UserRole approverRole,
            RoleApprovalDecisionRequest decisionRequest
    ) {
        return new RoleAssignmentApproval(
                requestId,
                approverUserId,
                approverRole,
                decisionRequest.getDecision(),
                decisionRequest.getRemarks()
        );
    }

    public RoleAssignmentApprovalResponse toResponse(
            RoleAssignmentApproval approval
    ) {
        return new RoleAssignmentApprovalResponse(
                approval.getId(),
                approval.getRequestId(),
                approval.getApproverUserId(),
                approval.getApproverRole(),
                approval.getDecision(),
                approval.getRemarks(),
                approval.getDecidedAt()
        );
    }
}