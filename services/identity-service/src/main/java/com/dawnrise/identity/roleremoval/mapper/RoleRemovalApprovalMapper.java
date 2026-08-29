package com.dawnrise.identity.roleremoval.mapper;

import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalApprovalResponse;
import com.dawnrise.identity.roleremoval.entity.RoleRemovalApproval;
import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.stereotype.Component;

@Component
public class RoleRemovalApprovalMapper {

    public RoleRemovalApproval toEntity(
            Long requestId,
            Long approverUserId,
            UserRole approverRole,
            RoleApprovalDecisionRequest decisionRequest
    ) {
        return new RoleRemovalApproval(
                requestId,
                approverUserId,
                approverRole,
                decisionRequest.getDecision(),
                decisionRequest.getRemarks()
        );
    }

    public RoleRemovalApprovalResponse toResponse(
            RoleRemovalApproval approval
    ) {
        return new RoleRemovalApprovalResponse(
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
