package com.dawnrise.identity.roleremoval.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalApprovalResponse;
import org.springframework.data.domain.Pageable;

public interface RoleRemovalApprovalService {

    RoleRemovalApprovalResponse recordDecision(
            Long organizationId,
            Long requestId,
            AuthorizationContext authorizationContext,
            RoleApprovalDecisionRequest decisionRequest
    );

    PageResponse<RoleRemovalApprovalResponse> getDecisionHistoryForApprover(
            Long organizationId,
            Long approverUserId,
            Pageable pageable
    );
}
