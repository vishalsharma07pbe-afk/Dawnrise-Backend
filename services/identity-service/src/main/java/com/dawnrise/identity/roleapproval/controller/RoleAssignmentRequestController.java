package com.dawnrise.identity.roleapproval.controller;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.roleapproval.dto.CreateRoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentRequestResponse;
import com.dawnrise.identity.roleapproval.service.RoleAssignmentRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentApprovalResponse;
import com.dawnrise.identity.roleapproval.service.RoleAssignmentApprovalService;
import com.dawnrise.identity.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentRequestDetailsResponse;

@RestController
@RequestMapping(
        "/api/v1/organizations/{organizationId}/role-requests"
)
@PreAuthorize(
        "@tenantSecurity.canAccessOrganization(authentication, #organizationId)"
)
public class RoleAssignmentRequestController {

    private final RoleAssignmentRequestService requestService;
    private final RoleAssignmentApprovalService approvalService;

    public RoleAssignmentRequestController(
            RoleAssignmentRequestService requestService,
            RoleAssignmentApprovalService approvalService
    ) {
        this.requestService = requestService;
        this.approvalService = approvalService;
    }

    @PostMapping
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_ASSIGNMENT_REQUEST_CREATE')
        """)
    public ResponseEntity<RoleAssignmentRequestResponse> createRequest(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRoleAssignmentRequest request
    ) {
        RoleAssignmentRequestResponse response =
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
        and hasAuthority('ROLE_ASSIGNMENT_REQUEST_VIEW')
        """)
    public ResponseEntity<RoleAssignmentRequestDetailsResponse> getRequestDetails(
            @PathVariable Long organizationId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long viewerUserId = Long.valueOf(
                jwt.getSubject()
        );

        RoleAssignmentRequestDetailsResponse response =
                requestService.getRequestDetails(
                        organizationId,
                        requestId,
                        viewerUserId
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/decision-history")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_ASSIGNMENT_REQUEST_VIEW')
        """)
    public ResponseEntity<PageResponse<RoleAssignmentApprovalResponse>>
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
        Long approverUserId = Long.valueOf(
                jwt.getSubject()
        );

        PageResponse<RoleAssignmentApprovalResponse> response =
                approvalService.getDecisionHistoryForApprover(
                        organizationId,
                        approverUserId,
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/decisions")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_ASSIGNMENT_APPROVE')
        """)
    public ResponseEntity<RoleAssignmentApprovalResponse> recordDecision(
            @PathVariable Long organizationId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RoleApprovalDecisionRequest decisionRequest
    ) {
        RoleAssignmentApprovalResponse response =
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

    @GetMapping("/actionable")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_ASSIGNMENT_REQUEST_VIEW')
        """)
    public ResponseEntity<PageResponse<RoleAssignmentRequestResponse>>
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
        Long approverUserId = Long.valueOf(
                jwt.getSubject()
        );

        PageResponse<RoleAssignmentRequestResponse> response =
                requestService.getActionableRequestsForApprover(
                        organizationId,
                        approverUserId,
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/cancel")
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('ROLE_ASSIGNMENT_REQUEST_CANCEL')
        """)
    public ResponseEntity<RoleAssignmentRequestResponse> cancelRequest(
            @PathVariable Long organizationId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        RoleAssignmentRequestResponse response =
                requestService.cancelRequest(
                        organizationId,
                        requestId,
                        AuthorizationContext.fromJwt(jwt)
                );

        return ResponseEntity.ok(response);
    }
}
