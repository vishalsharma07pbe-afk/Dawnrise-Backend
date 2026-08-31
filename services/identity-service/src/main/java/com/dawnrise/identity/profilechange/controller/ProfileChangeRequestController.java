package com.dawnrise.identity.profilechange.controller;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.profilechange.dto.CreateProfileChangeRequest;
import com.dawnrise.identity.profilechange.dto.ProfileChangeDecisionRequest;
import com.dawnrise.identity.profilechange.dto.ProfileChangeRequestResponse;
import com.dawnrise.identity.profilechange.enums.ProfileChangeRequestStatus;
import com.dawnrise.identity.profilechange.service.ProfileChangeRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/organizations/{organizationId}/profile-change-requests"
)
public class ProfileChangeRequestController {

    private final ProfileChangeRequestService profileChangeRequestService;

    public ProfileChangeRequestController(
            ProfileChangeRequestService profileChangeRequestService
    ) {
        this.profileChangeRequestService = profileChangeRequestService;
    }

    @PostMapping("/users/{targetUserId}")
    @PreAuthorize("""
            @organizationTokenSecurity.isOrganizationUser(authentication)
            and
            @tenantSecurity.canAccessOrganization(
                authentication,
                #organizationId
            )
            and hasAuthority('USER_PROFILE_UPDATE')
            """)
    public ResponseEntity<ProfileChangeRequestResponse> createRequest(
            @PathVariable Long organizationId,
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProfileChangeRequest request
    ) {
        ProfileChangeRequestResponse response =
                profileChangeRequestService.createRequest(
                        organizationId,
                        targetUserId,
                        AuthorizationContext.fromJwt(jwt),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("""
            @organizationTokenSecurity.isOrganizationUser(authentication)
            and
            @tenantSecurity.canAccessOrganization(
                authentication,
                #organizationId
            )
            """)
    public ResponseEntity<ProfileChangeRequestResponse> getRequest(
            @PathVariable Long organizationId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ProfileChangeRequestResponse response =
                profileChangeRequestService.getRequest(
                        organizationId,
                        requestId,
                        AuthorizationContext.fromJwt(jwt)
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{targetUserId}")
    @PreAuthorize("""
            @organizationTokenSecurity.isOrganizationUser(authentication)
            and
            @tenantSecurity.canAccessOrganization(
                authentication,
                #organizationId
            )
            and hasAuthority('PROFILE_UPDATE_SELF')
            """)
    public ResponseEntity<PageResponse<ProfileChangeRequestResponse>>
    getTargetUserRequests(
            @PathVariable Long organizationId,
            @PathVariable Long targetUserId,
            @RequestParam(required = false)
            ProfileChangeRequestStatus status,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "createdAt"
            )
            Pageable pageable
    ) {
        PageResponse<ProfileChangeRequestResponse> response =
                profileChangeRequestService.getTargetUserRequests(
                        organizationId,
                        targetUserId,
                        status,
                        AuthorizationContext.fromJwt(jwt),
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/submitted")
    @PreAuthorize("""
            @organizationTokenSecurity.isOrganizationUser(authentication)
            and
            @tenantSecurity.canAccessOrganization(
                authentication,
                #organizationId
            )
            and hasAuthority('USER_PROFILE_UPDATE')
            """)
    public ResponseEntity<PageResponse<ProfileChangeRequestResponse>>
    getSubmittedRequests(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "createdAt"
            )
            Pageable pageable
    ) {
        PageResponse<ProfileChangeRequestResponse> response =
                profileChangeRequestService.getSubmittedRequests(
                        organizationId,
                        AuthorizationContext.fromJwt(jwt),
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("""
            @organizationTokenSecurity.isOrganizationUser(authentication)
            and
            @tenantSecurity.canAccessOrganization(
                authentication,
                #organizationId
            )
            and hasAuthority('PROFILE_UPDATE_SELF')
            """)
    public ResponseEntity<ProfileChangeRequestResponse> approveRequest(
            @PathVariable Long organizationId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileChangeDecisionRequest request
    ) {
        ProfileChangeRequestResponse response =
                profileChangeRequestService.approveRequest(
                        organizationId,
                        requestId,
                        AuthorizationContext.fromJwt(jwt),
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("""
            @organizationTokenSecurity.isOrganizationUser(authentication)
            and
            @tenantSecurity.canAccessOrganization(
                authentication,
                #organizationId
            )
            and hasAuthority('PROFILE_UPDATE_SELF')
            """)
    public ResponseEntity<ProfileChangeRequestResponse> rejectRequest(
            @PathVariable Long organizationId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileChangeDecisionRequest request
    ) {
        ProfileChangeRequestResponse response =
                profileChangeRequestService.rejectRequest(
                        organizationId,
                        requestId,
                        AuthorizationContext.fromJwt(jwt),
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/cancel")
    @PreAuthorize("""
            @organizationTokenSecurity.isOrganizationUser(authentication)
            and
            @tenantSecurity.canAccessOrganization(
                authentication,
                #organizationId
            )
            and hasAuthority('USER_PROFILE_UPDATE')
            """)
    public ResponseEntity<ProfileChangeRequestResponse> cancelRequest(
            @PathVariable Long organizationId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileChangeDecisionRequest request
    ) {
        ProfileChangeRequestResponse response =
                profileChangeRequestService.cancelRequest(
                        organizationId,
                        requestId,
                        AuthorizationContext.fromJwt(jwt),
                        request
                );

        return ResponseEntity.ok(response);
    }
}