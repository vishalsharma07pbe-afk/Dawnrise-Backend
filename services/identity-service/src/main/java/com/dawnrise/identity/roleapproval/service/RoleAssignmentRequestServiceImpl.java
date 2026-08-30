package com.dawnrise.identity.roleapproval.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.roleapproval.dto.CreateRoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentRequestResponse;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleapproval.exception.InvalidRoleRequestException;
import com.dawnrise.identity.roleapproval.mapper.RoleAssignmentRequestMapper;
import com.dawnrise.identity.roleapproval.policy.RoleApprovalPolicy;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentRequestRepository;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentApprovalResponse;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentRequestDetailsResponse;
import com.dawnrise.identity.roleapproval.mapper.RoleAssignmentApprovalMapper;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentApprovalRepository;
import java.util.List;
import com.dawnrise.identity.roleapproval.exception.InvalidApprovalStateException;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;

import java.util.Map;
import java.util.Set;

@Service
public class RoleAssignmentRequestServiceImpl implements RoleAssignmentRequestService {

    private final RoleAssignmentRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final RoleApprovalPolicy approvalPolicy;
    private final RoleAssignmentRequestMapper requestMapper;
    private final RoleAssignmentApprovalRepository approvalRepository;
    private final RoleAssignmentApprovalMapper approvalMapper;
    private final SecurityAuditService auditService;
    private final RoleAssignmentWorkflowService workflowService;

    public RoleAssignmentRequestServiceImpl(
            RoleAssignmentRequestRepository requestRepository,
            RoleAssignmentApprovalRepository approvalRepository,
            UserRepository userRepository,
            RoleApprovalPolicy approvalPolicy,
            RoleAssignmentRequestMapper requestMapper,
            RoleAssignmentApprovalMapper approvalMapper,
            SecurityAuditService auditService,
            RoleAssignmentWorkflowService workflowService
    ) {
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.approvalPolicy = approvalPolicy;
        this.requestMapper = requestMapper;
        this.approvalMapper = approvalMapper;
        this.auditService = auditService;
        this.workflowService = workflowService;
    }

    @Override
    @Transactional
    public RoleAssignmentRequestResponse createRequest(
            Long organizationId,
            AuthorizationContext authorizationContext,
            CreateRoleAssignmentRequest request
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.ROLE_ASSIGNMENT_REQUEST_CREATE
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
                    "Only an active user can submit a role request"
            );
        }

        if (!approvalPolicy.canRequestApproval(
                requester.getRoles(),
                request.getRequestedRole()
        )) {
            throw new InvalidRoleRequestException(
                    "You are not allowed to request this role"
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

        if (requester.getId().equals(targetUser.getId())) {
            throw new InvalidRoleRequestException(
                    "You cannot request a role for yourself"
            );
        }

        if (targetUser.hasRole(request.getRequestedRole())) {
            throw new InvalidRoleRequestException(
                    "The user already has the requested role"
            );
        }

        boolean pendingRequestExists = requestRepository
                .existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                        organizationId,
                        targetUser.getId(),
                        request.getRequestedRole(),
                        ApprovalStatus.PENDING
                );

        if (pendingRequestExists) {
            throw new DuplicateResourceException(
                    "A pending request already exists for this user and role"
            );
        }

        RoleAssignmentRequest roleRequest =
                requestMapper.toEntity(
                        organizationId,
                        requester.getId(),
                        request
                );

        RoleAssignmentRequest savedRequest =
                requestRepository.save(roleRequest);

        auditService.record(
                organizationId,
                requester.getId(),
                SecurityAuditAction.ROLE_ASSIGNMENT_REQUEST_CREATE,
                SecurityAuditOutcome.SUCCESS,
                "ROLE_ASSIGNMENT_REQUEST",
                savedRequest.getId(),
                Map.of(
                        "targetUserId", targetUser.getId(),
                        "role", request.getRequestedRole()
                )
        );

        workflowService.applyRequesterSignOff(
                organizationId,
                savedRequest,
                requester
        );

        return requestMapper.toResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleAssignmentRequestResponse>
    getActionableRequestsForApprover(
            Long organizationId,
            Long approverUserId,
            Pageable pageable
    ) {
        User approver = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        approverUserId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver user not found"
                ));

        if (approver.getStatus() != UserStatus.ACTIVE) {
            throw new ApprovalNotAllowedException(
                    "Only an active user can view approval requests"
            );
        }

        Set<UserRole> reviewableRoles =
                approvalPolicy.getReviewableRoles(
                        approver.getRoles()
                );

        if (reviewableRoles.isEmpty()) {
            Page<RoleAssignmentRequestResponse> emptyPage =
                    Page.empty(pageable);

            return PageResponse.from(emptyPage);
        }

        Page<RoleAssignmentRequest> actionableRequests =
                requestRepository
                        .findActionableRequestsForApprover(
                                organizationId,
                                ApprovalStatus.PENDING,
                                reviewableRoles,
                                approverUserId,
                                pageable
                        );

        Page<RoleAssignmentRequestResponse> responsePage =
                actionableRequests.map(
                        requestMapper::toResponse
                );

        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleAssignmentRequestDetailsResponse getRequestDetails(
            Long organizationId,
            Long requestId,
            Long viewerUserId
    ) {
        RoleAssignmentRequest roleRequest = requestRepository
                .findByIdAndOrganizationId(
                        requestId,
                        organizationId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role assignment request not found"
                ));

        User viewer = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        viewerUserId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Viewing user not found"
                ));

        Set<UserRole> reviewableRoles =
                approvalPolicy.getReviewableRoles(
                        viewer.getRoles()
                );

        boolean isRequester = viewer.getId().equals(
                roleRequest.getRequestedByUserId()
        );

        boolean isTargetUser = viewer.getId().equals(
                roleRequest.getUserId()
        );

        boolean isEligibleApprover = reviewableRoles.contains(
                roleRequest.getRequestedRole()
        );

        if (!isRequester && !isTargetUser && !isEligibleApprover) {
            throw new ApprovalNotAllowedException(
                    "You are not allowed to view this role request"
            );
        }

        List<RoleAssignmentApprovalResponse> approvalHistory =
                approvalRepository
                        .findAllByRequestIdOrderByDecidedAtAsc(requestId)
                        .stream()
                        .map(approvalMapper::toResponse)
                        .toList();

        return new RoleAssignmentRequestDetailsResponse(
                requestMapper.toResponse(roleRequest),
                approvalHistory
        );
    }

    @Override
    @Transactional
    public RoleAssignmentRequestResponse cancelRequest(
            Long organizationId,
            Long requestId,
            AuthorizationContext authorizationContext
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.ROLE_ASSIGNMENT_REQUEST_CANCEL
        );

        RoleAssignmentRequest roleRequest = requestRepository
                .findByIdAndOrganizationId(
                        requestId,
                        organizationId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role assignment request not found"
                ));

        if (!roleRequest.getRequestedByUserId().equals(
                authorizationContext.getUserId()
        )) {
            throw new ApprovalNotAllowedException(
                    "Only the original requester can cancel this role request"
            );
        }

        if (!roleRequest.isPending()) {
            throw new InvalidApprovalStateException(
                    "Only a pending role request can be cancelled"
            );
        }

        roleRequest.cancel();
        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.ROLE_ASSIGNMENT_REQUEST_CANCEL,
                SecurityAuditOutcome.SUCCESS,
                "ROLE_ASSIGNMENT_REQUEST",
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
