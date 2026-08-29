package com.dawnrise.identity.roleremoval.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleapproval.exception.InvalidApprovalStateException;
import com.dawnrise.identity.roleapproval.exception.InvalidRoleRequestException;
import com.dawnrise.identity.roleremoval.dto.CreateRoleRemovalRequest;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalApprovalResponse;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalRequestDetailsResponse;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalRequestResponse;
import com.dawnrise.identity.roleremoval.entity.RoleRemovalRequest;
import com.dawnrise.identity.roleremoval.mapper.RoleRemovalApprovalMapper;
import com.dawnrise.identity.roleremoval.mapper.RoleRemovalRequestMapper;
import com.dawnrise.identity.roleremoval.policy.ProtectedRoleRemovalPolicy;
import com.dawnrise.identity.roleremoval.policy.RoleRemovalApprovalPolicy;
import com.dawnrise.identity.roleremoval.repository.RoleRemovalApprovalRepository;
import com.dawnrise.identity.roleremoval.repository.RoleRemovalRequestRepository;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoleRemovalRequestServiceImpl
        implements RoleRemovalRequestService {

    private final RoleRemovalRequestRepository requestRepository;
    private final RoleRemovalApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final RoleRemovalApprovalPolicy approvalPolicy;
    private final ProtectedRoleRemovalPolicy protectedRoleRemovalPolicy;
    private final RoleRemovalRequestMapper requestMapper;
    private final RoleRemovalApprovalMapper approvalMapper;
    private final SecurityAuditService auditService;

    public RoleRemovalRequestServiceImpl(
            RoleRemovalRequestRepository requestRepository,
            RoleRemovalApprovalRepository approvalRepository,
            UserRepository userRepository,
            RoleRemovalApprovalPolicy approvalPolicy,
            ProtectedRoleRemovalPolicy protectedRoleRemovalPolicy,
            RoleRemovalRequestMapper requestMapper,
            RoleRemovalApprovalMapper approvalMapper,
            SecurityAuditService auditService
    ) {
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.approvalPolicy = approvalPolicy;
        this.protectedRoleRemovalPolicy = protectedRoleRemovalPolicy;
        this.requestMapper = requestMapper;
        this.approvalMapper = approvalMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public RoleRemovalRequestResponse createRequest(
            Long organizationId,
            AuthorizationContext authorizationContext,
            CreateRoleRemovalRequest request
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.ROLE_REMOVAL_REQUEST_CREATE
        );

        User requester = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        authorizationContext.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Requesting user not found"
                ));

        if (requester.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRoleRequestException(
                    "Only an active user can submit a role removal request"
            );
        }

        if (!approvalPolicy.requiresApproval(request.getRequestedRole())) {
            throw new InvalidRoleRequestException(
                    "Routine role removal must use the routine role update endpoint"
            );
        }

        if (!approvalPolicy.canRequestRemoval(
                requester.getRoles(),
                request.getRequestedRole()
        )) {
            throw new InvalidRoleRequestException(
                    "You are not allowed to request removal of this role"
            );
        }

        User targetUser = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        request.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target user not found"
                ));

        if (!targetUser.hasRole(request.getRequestedRole())) {
            throw new InvalidRoleRequestException(
                    "The target user does not currently have the requested role"
            );
        }

        protectedRoleRemovalPolicy.assertRemovalAllowed(
                organizationId,
                targetUser,
                request.getRequestedRole()
        );

        boolean pendingRequestExists = requestRepository
                .existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                        organizationId,
                        targetUser.getId(),
                        request.getRequestedRole(),
                        ApprovalStatus.PENDING
                );

        if (pendingRequestExists) {
            throw new DuplicateResourceException(
                    "A pending removal request already exists for this user and role"
            );
        }

        RoleRemovalRequest roleRequest =
                requestMapper.toEntity(
                        organizationId,
                        requester.getId(),
                        request
                );

        RoleRemovalRequest savedRequest =
                requestRepository.save(roleRequest);

        auditService.record(
                organizationId,
                requester.getId(),
                SecurityAuditAction.ROLE_REMOVAL_REQUEST_CREATE,
                SecurityAuditOutcome.SUCCESS,
                "ROLE_REMOVAL_REQUEST",
                savedRequest.getId(),
                Map.of(
                        "targetUserId", targetUser.getId(),
                        "role", request.getRequestedRole()
                )
        );

        return requestMapper.toResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleRemovalRequestDetailsResponse getRequestDetails(
            Long organizationId,
            Long requestId,
            Long viewerUserId
    ) {
        RoleRemovalRequest roleRequest = requestRepository
                .findByIdAndOrganizationId(requestId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role removal request not found"
                ));

        User viewer = userRepository
                .findByOrganizationIdAndId(organizationId, viewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Viewing user not found"
                ));

        Set<UserRole> reviewableRoles =
                approvalPolicy.getReviewableRoles(viewer.getRoles());

        boolean isRequester =
                viewer.getId().equals(roleRequest.getRequestedByUserId());
        boolean isTargetUser =
                viewer.getId().equals(roleRequest.getUserId());
        boolean isEligibleApprover =
                reviewableRoles.contains(roleRequest.getRequestedRole());

        if (!isRequester && !isTargetUser && !isEligibleApprover) {
            throw new ApprovalNotAllowedException(
                    "You are not allowed to view this role removal request"
            );
        }

        List<RoleRemovalApprovalResponse> approvalHistory =
                approvalRepository
                        .findAllByRequestIdOrderByDecidedAtAsc(requestId)
                        .stream()
                        .map(approvalMapper::toResponse)
                        .toList();

        return new RoleRemovalRequestDetailsResponse(
                requestMapper.toResponse(roleRequest),
                approvalHistory
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleRemovalRequestResponse>
    getActionableRequestsForApprover(
            Long organizationId,
            Long approverUserId,
            Pageable pageable
    ) {
        User approver = userRepository
                .findByOrganizationIdAndId(organizationId, approverUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver user not found"
                ));

        if (approver.getStatus() != UserStatus.ACTIVE) {
            throw new ApprovalNotAllowedException(
                    "Only an active user can view removal requests"
            );
        }

        Set<UserRole> reviewableRoles =
                approvalPolicy.getReviewableRoles(approver.getRoles());

        if (reviewableRoles.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        return PageResponse.from(
                requestRepository
                        .findActionableRequestsForApprover(
                                organizationId,
                                ApprovalStatus.PENDING,
                                reviewableRoles,
                                approverUserId,
                                pageable
                        )
                        .map(requestMapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleRemovalRequestResponse> getRequesterHistory(
            Long organizationId,
            Long requesterUserId,
            Pageable pageable
    ) {
        return PageResponse.from(
                requestRepository
                        .findAllByOrganizationIdAndRequestedByUserId(
                                organizationId,
                                requesterUserId,
                                pageable
                        )
                        .map(requestMapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleRemovalRequestResponse> getTargetUserHistory(
            Long organizationId,
            Long targetUserId,
            Pageable pageable
    ) {
        return PageResponse.from(
                requestRepository
                        .findAllByOrganizationIdAndUserId(
                                organizationId,
                                targetUserId,
                                pageable
                        )
                        .map(requestMapper::toResponse)
        );
    }

    @Override
    @Transactional
    public RoleRemovalRequestResponse cancelRequest(
            Long organizationId,
            Long requestId,
            AuthorizationContext authorizationContext
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.ROLE_REMOVAL_REQUEST_CANCEL
        );

        RoleRemovalRequest roleRequest = requestRepository
                .findByIdAndOrganizationId(requestId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role removal request not found"
                ));

        if (!roleRequest.getRequestedByUserId().equals(
                authorizationContext.getUserId()
        )) {
            throw new ApprovalNotAllowedException(
                    "Only the original requester can cancel this role removal request"
            );
        }

        if (!roleRequest.isPending()) {
            throw new InvalidApprovalStateException(
                    "Only a pending role removal request can be cancelled"
            );
        }

        roleRequest.cancel();
        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.ROLE_REMOVAL_REQUEST_CANCEL,
                SecurityAuditOutcome.SUCCESS,
                "ROLE_REMOVAL_REQUEST",
                roleRequest.getId(),
                Map.of(
                        "targetUserId", roleRequest.getUserId(),
                        "role", roleRequest.getRequestedRole()
                )
        );
        return requestMapper.toResponse(roleRequest);
    }

    private void requirePermission(
            AuthorizationContext authorizationContext,
            PermissionCode permissionCode
    ) {
        if (authorizationContext == null
                || !authorizationContext.hasPermission(permissionCode)) {
            throw new ApprovalNotAllowedException(
                    "Missing required permission: " + permissionCode
            );
        }
    }
}
