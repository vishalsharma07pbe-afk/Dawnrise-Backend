package com.dawnrise.identity.roleremoval.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.roleremoval.dto.CreateRoleRemovalRequest;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalRequestDetailsResponse;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalRequestResponse;
import org.springframework.data.domain.Pageable;

public interface RoleRemovalRequestService {

    RoleRemovalRequestResponse createRequest(
            Long organizationId,
            AuthorizationContext authorizationContext,
            CreateRoleRemovalRequest request
    );

    RoleRemovalRequestDetailsResponse getRequestDetails(
            Long organizationId,
            Long requestId,
            Long viewerUserId
    );

    PageResponse<RoleRemovalRequestResponse> getActionableRequestsForApprover(
            Long organizationId,
            Long approverUserId,
            Pageable pageable
    );

    PageResponse<RoleRemovalRequestResponse> getRequesterHistory(
            Long organizationId,
            Long requesterUserId,
            Pageable pageable
    );

    PageResponse<RoleRemovalRequestResponse> getTargetUserHistory(
            Long organizationId,
            Long targetUserId,
            Pageable pageable
    );

    RoleRemovalRequestResponse cancelRequest(
            Long organizationId,
            Long requestId,
            AuthorizationContext authorizationContext
    );
}
