package com.dawnrise.identity.roleremoval.controller;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleremoval.dto.CreateRoleRemovalRequest;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalApprovalResponse;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalRequestDetailsResponse;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalRequestResponse;
import com.dawnrise.identity.roleremoval.service.RoleRemovalApprovalService;
import com.dawnrise.identity.roleremoval.service.RoleRemovalRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/api/v1/organizations/{organizationId}/role-removal-requests"
)
@PreAuthorize(
        "@tenantSecurity.canAccessOrganization(authentication, #organizationId)"
)
public class RoleRemovalRequestController {

    private final RoleRemovalRequestService requestService;
    private final RoleRemovalApprovalService approvalService;

    public RoleRemovalRequestController(
            RoleRemovalRequestService requestService,
            RoleRemovalApprovalService approvalService
    ) {
        this.requestService = requestService;
        this.approvalService = approvalService;
    }

    @PostMapping
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_REMOVAL_REQUEST_CREATE')
        """)
    public ResponseEntity<RoleRemovalRequestResponse> createRequest(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRoleRemovalRequest request
    ) {
        RoleRemovalRequestResponse response =
                requestService.createRequest(
                        organizationId,
                        AuthorizationContext.fromJwt(jwt),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_REMOVAL_REQUEST_VIEW')
        """)
    public ResponseEntity<RoleRemovalRequestDetailsResponse> getRequestDetails(
            @PathVariable Long organizationId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        RoleRemovalRequestDetailsResponse response =
                requestService.getRequestDetails(
                        organizationId,
                        requestId,
                        Long.valueOf(jwt.getSubject())
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/actionable")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_REMOVAL_REQUEST_VIEW')
        """)
    public ResponseEntity<PageResponse<RoleRemovalRequestResponse>>
    getActionableRequests(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        PageResponse<RoleRemovalRequestResponse> response =
                requestService.getActionableRequestsForApprover(
                        organizationId,
                        Long.valueOf(jwt.getSubject()),
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_REMOVAL_REQUEST_VIEW')
        """)
    public ResponseEntity<PageResponse<RoleRemovalRequestResponse>>
    getRequesterHistory(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        PageResponse<RoleRemovalRequestResponse> response =
                requestService.getRequesterHistory(
                        organizationId,
                        Long.valueOf(jwt.getSubject()),
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/target-users/{targetUserId}/history")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_REMOVAL_REQUEST_VIEW')
        """)
    public ResponseEntity<PageResponse<RoleRemovalRequestResponse>>
    getTargetUserHistory(
            @PathVariable Long organizationId,
            @PathVariable Long targetUserId,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        PageResponse<RoleRemovalRequestResponse> response =
                requestService.getTargetUserHistory(
                        organizationId,
                        targetUserId,
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/decision-history")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_REMOVAL_REQUEST_VIEW')
        """)
    public ResponseEntity<PageResponse<RoleRemovalApprovalResponse>>
    getDecisionHistory(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(
                    size = 20,
                    sort = "decidedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        PageResponse<RoleRemovalApprovalResponse> response =
                approvalService.getDecisionHistoryForApprover(
                        organizationId,
                        Long.valueOf(jwt.getSubject()),
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/decisions")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_REMOVAL_APPROVE')
        """)
    public ResponseEntity<RoleRemovalApprovalResponse> recordDecision(
            @PathVariable Long organizationId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RoleApprovalDecisionRequest decisionRequest
    ) {
        RoleRemovalApprovalResponse response =
                approvalService.recordDecision(
                        organizationId,
                        requestId,
                        AuthorizationContext.fromJwt(jwt),
                        decisionRequest
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{requestId}/cancel")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_REMOVAL_REQUEST_CANCEL')
        """)
    public ResponseEntity<RoleRemovalRequestResponse> cancelRequest(
            @PathVariable Long organizationId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        RoleRemovalRequestResponse response =
                requestService.cancelRequest(
                        organizationId,
                        requestId,
                        AuthorizationContext.fromJwt(jwt)
                );

        return ResponseEntity.ok(response);
    }
}
