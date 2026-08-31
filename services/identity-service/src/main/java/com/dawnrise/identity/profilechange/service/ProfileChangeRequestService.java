package com.dawnrise.identity.profilechange.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.profilechange.dto.CreateProfileChangeRequest;
import com.dawnrise.identity.profilechange.dto.ProfileChangeDecisionRequest;
import com.dawnrise.identity.profilechange.dto.ProfileChangeRequestResponse;
import com.dawnrise.identity.profilechange.enums.ProfileChangeRequestStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProfileChangeRequestService {

    ProfileChangeRequestResponse createRequest(
            Long organizationId,
            Long targetUserId,
            AuthorizationContext authorizationContext,
            CreateProfileChangeRequest request
    );

    ProfileChangeRequestResponse getRequest(
            Long organizationId,
            UUID requestId,
            AuthorizationContext authorizationContext
    );

    PageResponse<ProfileChangeRequestResponse> getTargetUserRequests(
            Long organizationId,
            Long targetUserId,
            ProfileChangeRequestStatus status,
            AuthorizationContext authorizationContext,
            Pageable pageable
    );

    PageResponse<ProfileChangeRequestResponse> getSubmittedRequests(
            Long organizationId,
            AuthorizationContext authorizationContext,
            Pageable pageable
    );

    ProfileChangeRequestResponse approveRequest(
            Long organizationId,
            UUID requestId,
            AuthorizationContext authorizationContext,
            ProfileChangeDecisionRequest request
    );

    ProfileChangeRequestResponse rejectRequest(
            Long organizationId,
            UUID requestId,
            AuthorizationContext authorizationContext,
            ProfileChangeDecisionRequest request
    );

    ProfileChangeRequestResponse cancelRequest(
            Long organizationId,
            UUID requestId,
            AuthorizationContext authorizationContext,
            ProfileChangeDecisionRequest request
    );
}