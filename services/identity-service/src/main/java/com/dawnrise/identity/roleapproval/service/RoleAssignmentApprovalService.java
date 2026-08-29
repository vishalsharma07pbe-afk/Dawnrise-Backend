package com.dawnrise.identity.roleapproval.service;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentApprovalResponse;
import org.springframework.data.domain.Pageable;

public interface RoleAssignmentApprovalService {

    RoleAssignmentApprovalResponse recordDecision(
            Long organizationId,
            Long requestId,
            AuthorizationContext authorizationContext,
            RoleApprovalDecisionRequest decisionRequest
    );

    PageResponse<RoleAssignmentApprovalResponse> getDecisionHistoryForApprover(
            Long organizationId,
            Long approverUserId,
            Pageable pageable
    );
}
