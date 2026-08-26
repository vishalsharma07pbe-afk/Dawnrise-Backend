package com.edusphere.identity.platform.user.service;

import com.edusphere.identity.common.dto.PageResponse;
import com.edusphere.identity.platform.auth.security.PlatformAuthorizationContext;
import com.edusphere.identity.platform.user.dto.CreatePlatformUserRequest;
import com.edusphere.identity.platform.user.dto.PlatformUserResponse;
import com.edusphere.identity.platform.user.enums.PlatformUserStatus;
import org.springframework.data.domain.Pageable;

public interface PlatformUserService {

    PlatformUserResponse createPlatformUser(
            PlatformAuthorizationContext authorizationContext,
            CreatePlatformUserRequest request
    );

    PlatformUserResponse getPlatformUserById(
            PlatformAuthorizationContext authorizationContext,
            Long platformUserId
    );

    PageResponse<PlatformUserResponse> getPlatformUsers(
            PlatformAuthorizationContext authorizationContext,
            PlatformUserStatus status,
            String search,
            Pageable pageable
    );

    void resendActivationLink(
            PlatformAuthorizationContext authorizationContext,
            Long platformUserId
    );
}
