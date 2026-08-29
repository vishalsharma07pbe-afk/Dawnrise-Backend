package com.dawnrise.identity.roleremoval.service;

import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalDecision;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleapproval.exception.InvalidApprovalStateException;
import com.dawnrise.identity.roleapproval.exception.InvalidRoleRequestException;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalApprovalResponse;
import com.dawnrise.identity.roleremoval.entity.RoleRemovalApproval;
import com.dawnrise.identity.roleremoval.entity.RoleRemovalRequest;
import com.dawnrise.identity.roleremoval.mapper.RoleRemovalApprovalMapper;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoleRemovalApprovalServiceImpl
        implements RoleRemovalApprovalService {

    private final RoleRemovalRequestRepository requestRepository;
    private final RoleRemovalApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final RoleRemovalApprovalPolicy approvalPolicy;
    private final ProtectedRoleRemovalPolicy protectedRoleRemovalPolicy;
    private final RoleRemovalApprovalMapper approvalMapper;
    private final RefreshTokenService refreshTokenService;
    private final SecurityAuditService auditService;

    public RoleRemovalApprovalServiceImpl(
            RoleRemovalRequestRepository requestRepository,
            RoleRemovalApprovalRepository approvalRepository,
            UserRepository userRepository,
            RoleRemovalApprovalPolicy approvalPolicy,
            ProtectedRoleRemovalPolicy protectedRoleRemovalPolicy,
            RoleRemovalApprovalMapper approvalMapper,
            RefreshTokenService refreshTokenService,
            SecurityAuditService auditService
    ) {
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.approvalPolicy = approvalPolicy;
        this.protectedRoleRemovalPolicy = protectedRoleRemovalPolicy;
        this.approvalMapper = approvalMapper;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public RoleRemovalApprovalResponse recordDecision(
            Long organizationId,
            Long requestId,
            AuthorizationContext authorizationContext,
            RoleApprovalDecisionRequest decisionRequest
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.ROLE_REMOVAL_APPROVE
        );

        RoleRemovalRequest roleRequest = requestRepository
                .findByIdAndOrganizationId(requestId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role removal request not found"
                ));

        if (!roleRequest.isPending()) {
            throw new InvalidApprovalStateException(
                    "Only a pending role removal request can be reviewed"
            );
        }

        User approver = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        authorizationContext.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver user not found"
                ));

        if (approver.getStatus() != UserStatus.ACTIVE) {
            throw new ApprovalNotAllowedException(
                    "Only an active user can review a role removal request"
            );
        }

        if (roleRequest.getRequestedByUserId().equals(approver.getId())) {
            throw new ApprovalNotAllowedException(
                    "The requester cannot approve their own role removal request"
            );
        }

        if (roleRequest.getUserId().equals(approver.getId())) {
            throw new ApprovalNotAllowedException(
                    "A user cannot approve removal of their own sensitive role"
            );
        }

        if (approvalRepository.existsByRequestIdAndApproverUserId(
                requestId,
                approver.getId()
        )) {
            throw new DuplicateResourceException(
                    "You have already decided on this role removal request"
            );
        }

        List<RoleRemovalApproval> existingApprovals =
                approvalRepository.findAllByRequestId(requestId);

        Set<UserRole> satisfiedApproverRoles = existingApprovals
                .stream()
                .filter(approval ->
                        approval.getDecision() == ApprovalDecision.APPROVED
                )
                .map(RoleRemovalApproval::getApproverRole)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);

        UserRole actingApproverRole = approvalPolicy
                .findAvailableApproverRole(
                        roleRequest.getRequestedRole(),
                        approver.getRoles(),
                        satisfiedApproverRoles
                )
                .orElseThrow(() -> new ApprovalNotAllowedException(
                        "You are not authorized to review this role removal request"
                ));

        RoleRemovalApproval approval =
                approvalMapper.toEntity(
                        roleRequest.getId(),
                        approver.getId(),
                        actingApproverRole,
                        decisionRequest
                );

        RoleRemovalApproval savedApproval =
                approvalRepository.save(approval);

        auditService.record(
                organizationId,
                approver.getId(),
                SecurityAuditAction.ROLE_REMOVAL_DECISION,
                decisionRequest.getDecision() == ApprovalDecision.REJECTED
                        ? SecurityAuditOutcome.REJECTED
                        : SecurityAuditOutcome.SUCCESS,
                "ROLE_REMOVAL_REQUEST",
                roleRequest.getId(),
                Map.of(
                        "targetUserId", roleRequest.getUserId(),
                        "role", roleRequest.getRequestedRole(),
                        "decision", decisionRequest.getDecision()
                )
        );

        if (decisionRequest.getDecision() == ApprovalDecision.REJECTED) {
            roleRequest.reject();
            return approvalMapper.toResponse(savedApproval);
        }

        satisfiedApproverRoles.add(actingApproverRole);

        if (satisfiedApproverRoles.containsAll(
                approvalPolicy.getRequiredApproverRoles(
                        roleRequest.getRequestedRole()
                )
        )) {
            completeApprovedRemoval(
                    organizationId,
                    roleRequest,
                    approver.getId()
            );
        }

        return approvalMapper.toResponse(savedApproval);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleRemovalApprovalResponse>
    getDecisionHistoryForApprover(
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
                    "Only an active user can view decision history"
            );
        }

        return PageResponse.from(
                approvalRepository
                        .findDecisionHistoryForApprover(
                                organizationId,
                                approver.getId(),
                                pageable
                        )
                        .map(approvalMapper::toResponse)
        );
    }

    private void completeApprovedRemoval(
            Long organizationId,
            RoleRemovalRequest roleRequest,
            Long finalApproverUserId
    ) {
        User targetUser = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        roleRequest.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target user not found"
                ));

        if (!targetUser.hasRole(roleRequest.getRequestedRole())) {
            throw new InvalidRoleRequestException(
                    "The target user no longer has the requested role"
            );
        }

        protectedRoleRemovalPolicy.assertRemovalAllowed(
                organizationId,
                targetUser,
                roleRequest.getRequestedRole()
        );

        targetUser.removeRole(roleRequest.getRequestedRole());
        roleRequest.approve();
        refreshTokenService.revokeAllForUser(targetUser.getId());
        auditService.record(
                organizationId,
                finalApproverUserId,
                SecurityAuditAction.ROLE_REMOVAL_FINAL_REMOVE,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                targetUser.getId(),
                Map.of(
                        "requestId", roleRequest.getId(),
                        "role", roleRequest.getRequestedRole()
                )
        );
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
