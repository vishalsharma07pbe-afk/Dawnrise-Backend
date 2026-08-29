package com.dawnrise.identity.roleapproval.service;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.roleapproval.dto.CreateRoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentRequestResponse;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentRequestDetailsResponse;
import org.springframework.data.domain.Pageable;

public interface RoleAssignmentRequestService {

    RoleAssignmentRequestResponse createRequest(
            Long organizationId,
            AuthorizationContext authorizationContext,
            CreateRoleAssignmentRequest request
    );

    PageResponse<RoleAssignmentRequestResponse>
    getActionableRequestsForApprover(
            Long organizationId,
            Long approverUserId,
            Pageable pageable
    );

    RoleAssignmentRequestDetailsResponse getRequestDetails(
            Long organizationId,
            Long requestId,
            Long viewerUserId
    );

    RoleAssignmentRequestResponse cancelRequest(
            Long organizationId,
            Long requestId,
            AuthorizationContext authorizationContext
    );
}
