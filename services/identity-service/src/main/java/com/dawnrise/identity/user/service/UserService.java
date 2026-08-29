package com.dawnrise.identity.user.service;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.user.dto.CreateUserRequest;
import com.dawnrise.identity.user.dto.UpdateUserProfileRequest;
import com.dawnrise.identity.user.dto.UpdateUserRolesRequest;
import com.dawnrise.identity.user.dto.UpdateUserStatusRequest;
import com.dawnrise.identity.user.dto.UserResponse;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse createUser(
            Long organizationId,
            AuthorizationContext authorizationContext,
            CreateUserRequest request
    );

    UserResponse getUserById(
            Long organizationId,
            Long userId
    );

    PageResponse<UserResponse> getAllUsersByOrganization(
            Long organizationId,
            Pageable pageable
    );

    UserResponse updateUserProfile(
            Long organizationId,
            Long userId,
            UpdateUserProfileRequest request
    );

    UserResponse updateUserRoles(
            Long organizationId,
            AuthorizationContext authorizationContext,
            Long userId,
            UpdateUserRolesRequest request
    );

    UserResponse updateUserStatus(
            Long organizationId,
            AuthorizationContext authorizationContext,
            Long userId,
            UpdateUserStatusRequest request
    );

    void resendActivationLink(
            Long organizationId,
            Long userId,
            AuthorizationContext authorizationContext
    );
}
