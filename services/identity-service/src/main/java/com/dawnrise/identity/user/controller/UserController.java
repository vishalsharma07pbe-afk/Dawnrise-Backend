package com.dawnrise.identity.user.controller;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.user.dto.CreateUserRequest;
import com.dawnrise.identity.user.dto.UpdateUserProfileRequest;
import com.dawnrise.identity.user.dto.UpdateUserRolesRequest;
import com.dawnrise.identity.user.dto.UpdateUserStatusRequest;
import com.dawnrise.identity.user.dto.UserResponse;
import com.dawnrise.identity.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('USER_CREATE')
        and @userAuthorization.canCreateUser(
            authentication,
            #request.roles
        )
        """)
    public ResponseEntity<UserResponse> createUser(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUserRequest request
    ) {
        if (!organizationId.equals(request.getOrganizationId())) {
            throw new IllegalArgumentException(
                    "Organization ID in the URL must match the request body"
            );
        }

        // The JWT subject contains the authenticated creator's database user ID.
        UserResponse response = userService.createUser(
                organizationId,
                AuthorizationContext.fromJwt(jwt),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and (
            (
                @tenantSecurity.isCurrentUser(authentication, #userId)
                and hasAuthority('PROFILE_VIEW_SELF')
            )
            or hasAuthority('USER_VIEW')
        )
        """)
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long organizationId,
            @PathVariable Long userId
    ) {
        UserResponse response = userService.getUserById(organizationId, userId);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('USER_VIEW')
        """)
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @PathVariable Long organizationId,
            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "firstName"
            )
            Pageable pageable
    ) {
        PageResponse<UserResponse> response =
                userService.getAllUsersByOrganization(
                        organizationId,
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and (
            (
                @tenantSecurity.isCurrentUser(authentication, #userId)
                and hasAuthority('PROFILE_UPDATE_SELF')
            )
            or hasAuthority('USER_PROFILE_UPDATE')
        )
        """)
    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserResponse> updateUserProfile(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            @Valid @RequestBody
            UpdateUserProfileRequest request
    ) {
        UserResponse response =
                userService.updateUserProfile(
                        organizationId,
                        userId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAnyAuthority(
            'ROLE_ASSIGN_ROUTINE',
            'ROLE_REMOVE_ROUTINE',
            'ROLE_ASSIGNMENT_REQUEST_CREATE'
        )
        """)
    public ResponseEntity<UserResponse> updateUserRoles(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        // Identify the authenticated person performing the role update.
        UserResponse response = userService.updateUserRoles(
                organizationId,
                AuthorizationContext.fromJwt(jwt),
                userId,
                request
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and
        @tenantSecurity.canAccessOrganization(
            authentication,
            #organizationId
        )
        and @userAuthorization.canUpdateStatus(
            authentication,
            #organizationId,
            #userId,
            #request.status
        )
        """)
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody
            UpdateUserStatusRequest request
    ) {
        UserResponse response =
                userService.updateUserStatus(
                        organizationId,
                        AuthorizationContext.fromJwt(jwt),
                        userId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/activation/resend")
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('USER_ACTIVATION_RESEND')
        """)
    public ResponseEntity<Void> resendActivationLink(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        userService.resendActivationLink(
                organizationId,
                userId,
                AuthorizationContext.fromJwt(jwt)
        );

        return ResponseEntity.accepted().build();
    }
}
