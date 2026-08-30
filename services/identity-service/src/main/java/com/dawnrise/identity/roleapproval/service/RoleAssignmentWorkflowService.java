package com.dawnrise.identity.roleapproval.service;

import com.dawnrise.identity.auth.activation.event.UserActivationRequestedEvent;
import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentApproval;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalDecision;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleapproval.mapper.RoleAssignmentApprovalMapper;
import com.dawnrise.identity.roleapproval.policy.RoleApprovalPolicy;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentApprovalRepository;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentRequestRepository;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RoleAssignmentWorkflowService {

    private static final String REQUESTER_SIGN_OFF_REMARKS =
            "System-recorded requester sign-off";

    private final RoleAssignmentRequestRepository requestRepository;
    private final RoleAssignmentApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final RoleApprovalPolicy approvalPolicy;
    private final RoleAssignmentApprovalMapper approvalMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityAuditService auditService;

    public RoleAssignmentWorkflowService(
            RoleAssignmentRequestRepository requestRepository,
            RoleAssignmentApprovalRepository approvalRepository,
            UserRepository userRepository,
            RoleApprovalPolicy approvalPolicy,
            RoleAssignmentApprovalMapper approvalMapper,
            ApplicationEventPublisher eventPublisher,
            SecurityAuditService auditService
    ) {
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.approvalPolicy = approvalPolicy;
        this.approvalMapper = approvalMapper;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    public Optional<RoleAssignmentApproval> applyRequesterSignOff(
            Long organizationId,
            RoleAssignmentRequest roleRequest,
            User requester
    ) {
        Optional<UserRole> actingRequesterRole =
                approvalPolicy.findAvailableApproverRole(
                        roleRequest.getRequestedRole(),
                        requester.getRoles(),
                        Set.of()
                );

        if (actingRequesterRole.isEmpty()) {
            return Optional.empty();
        }

        RoleApprovalDecisionRequest decisionRequest =
                new RoleApprovalDecisionRequest(
                        ApprovalDecision.APPROVED,
                        REQUESTER_SIGN_OFF_REMARKS
                );

        RoleAssignmentApproval approval =
                approvalMapper.toEntity(
                        roleRequest.getId(),
                        requester.getId(),
                        actingRequesterRole.get(),
                        decisionRequest
                );

        RoleAssignmentApproval savedApproval =
                approvalRepository.save(approval);

        auditService.record(
                organizationId,
                requester.getId(),
                SecurityAuditAction.ROLE_ASSIGNMENT_DECISION,
                SecurityAuditOutcome.SUCCESS,
                "ROLE_ASSIGNMENT_REQUEST",
                roleRequest.getId(),
                Map.of(
                        "targetUserId", roleRequest.getUserId(),
                        "role", roleRequest.getRequestedRole(),
                        "decision", ApprovalDecision.APPROVED,
                        "actingRequesterRole", actingRequesterRole.get(),
                        "requesterSignOff", true
                )
        );

        finalizeIfFullyApproved(
                organizationId,
                roleRequest,
                requester.getId()
        );

        return Optional.of(savedApproval);
    }

    public boolean finalizeIfFullyApproved(
            Long organizationId,
            RoleAssignmentRequest roleRequest,
            Long actorUserId
    ) {
        if (!roleRequest.isPending()) {
            return false;
        }

        Set<UserRole> satisfiedApproverRoles =
                approvalRepository.findAllByRequestId(roleRequest.getId())
                        .stream()
                        .filter(approval ->
                                approval.getDecision()
                                        == ApprovalDecision.APPROVED
                        )
                        .map(RoleAssignmentApproval::getApproverRole)
                        .collect(
                                HashSet::new,
                                HashSet::add,
                                HashSet::addAll
                        );

        Set<UserRole> requiredApproverRoles =
                approvalPolicy.getRequiredApproverRoles(
                        roleRequest.getRequestedRole()
                );

        if (!satisfiedApproverRoles.containsAll(requiredApproverRoles)) {
            return false;
        }

        User targetUser = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        roleRequest.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target user not found"
                ));

        targetUser.addRole(roleRequest.getRequestedRole());
        roleRequest.approve();

        auditService.record(
                organizationId,
                actorUserId,
                SecurityAuditAction.ROLE_ASSIGNMENT_FINAL_ASSIGN,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                targetUser.getId(),
                Map.of(
                        "requestId", roleRequest.getId(),
                        "targetUserId", targetUser.getId(),
                        "role", roleRequest.getRequestedRole()
                )
        );

        updateOnboardingStatusIfComplete(
                organizationId,
                roleRequest
        );

        return true;
    }

    public void updateOnboardingStatusIfComplete(
            Long organizationId,
            RoleAssignmentRequest completedRequest
    ) {
        User targetUser = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        completedRequest.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target user not found"
                ));

        if (targetUser.getStatus()
                != UserStatus.PENDING_APPROVAL) {
            return;
        }

        boolean anotherPendingRequestExists =
                requestRepository
                        .existsByOrganizationIdAndUserIdAndStatusAndIdNot(
                                organizationId,
                                targetUser.getId(),
                                ApprovalStatus.PENDING,
                                completedRequest.getId()
                        );

        if (anotherPendingRequestExists) {
            return;
        }

        if (!targetUser.getRoles().isEmpty()) {
            targetUser.setStatus(
                    UserStatus.PENDING_ACTIVATION
            );

            eventPublisher.publishEvent(
                    new UserActivationRequestedEvent(
                            targetUser.getId()
                    )
            );
        } else {
            targetUser.setStatus(UserStatus.INACTIVE);
        }
    }
}
