package com.dawnrise.identity.roleapproval.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentApprovalResponse;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentApproval;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalDecision;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleapproval.exception.InvalidApprovalStateException;
import com.dawnrise.identity.roleapproval.mapper.RoleAssignmentApprovalMapper;
import com.dawnrise.identity.roleapproval.policy.RoleApprovalPolicy;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentApprovalRepository;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentRequestRepository;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentApprovalServiceImplTest {

    @Mock
    private RoleAssignmentRequestRepository requestRepository;
    @Mock
    private RoleAssignmentApprovalRepository approvalRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleApprovalPolicy approvalPolicy;
    @Mock
    private RoleAssignmentApprovalMapper approvalMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SecurityAuditService auditService;

    private RoleAssignmentApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentApprovalServiceImpl(
                requestRepository,
                approvalRepository,
                userRepository,
                approvalPolicy,
                approvalMapper,
                eventPublisher,
                auditService
        );
    }

    @Test
    void recordDecision_whenRequestMissing_throwsResourceNotFoundException() {
        RoleApprovalDecisionRequest decision = decision(ApprovalDecision.APPROVED);

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.recordDecision(1L, 100L, auth(30L), decision)
        );
    }

    @Test
    void recordDecision_whenRequestNotPending_throwsInvalidApprovalStateException() {
        RoleAssignmentRequest roleRequest = roleRequest();
        roleRequest.reject();

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(roleRequest));

        assertThrows(
                InvalidApprovalStateException.class,
                () -> service.recordDecision(
                        1L,
                        100L,
                        auth(30L),
                        decision(ApprovalDecision.APPROVED)
                )
        );
        verify(userRepository, never()).findByOrganizationIdAndId(anyLong(), anyLong());
    }

    @Test
    void recordDecision_whenApproverIsRequester_throwsApprovalNotAllowedException() {
        RoleAssignmentRequest roleRequest = roleRequest();
        User approver = user(10L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(roleRequest));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(approver));

        ApprovalNotAllowedException exception = assertThrows(
                ApprovalNotAllowedException.class,
                () -> service.recordDecision(
                        1L,
                        100L,
                        auth(10L),
                        decision(ApprovalDecision.APPROVED)
                )
        );

        assertEquals(
                "The requester cannot approve their own request",
                exception.getMessage()
        );
    }

    @Test
    void recordDecision_whenAlreadyDecided_throwsDuplicateResourceException() {
        RoleAssignmentRequest roleRequest = roleRequest();
        User approver = user(30L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(roleRequest));
        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));
        when(approvalRepository.existsByRequestIdAndApproverUserId(100L, 30L))
                .thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> service.recordDecision(
                        1L,
                        100L,
                        auth(30L),
                        decision(ApprovalDecision.APPROVED)
                )
        );

        assertEquals(
                "You have already decided on this role request",
                exception.getMessage()
        );
    }

    @Test
    void recordDecision_whenRejected_savesApprovalAndRejectsRequest() {
        RoleAssignmentRequest roleRequest = roleRequest();
        User approver = user(30L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);
        User targetUser = user(20L, Set.of(UserRole.TEACHER), UserStatus.ACTIVE);
        RoleApprovalDecisionRequest decision = decision(ApprovalDecision.REJECTED);
        RoleAssignmentApproval approval = approval(UserRole.ADMIN, ApprovalDecision.REJECTED);
        RoleAssignmentApprovalResponse response = new RoleAssignmentApprovalResponse();

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(roleRequest));
        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(targetUser));
        when(approvalRepository.existsByRequestIdAndApproverUserId(100L, 30L))
                .thenReturn(false);
        when(approvalRepository.findAllByRequestId(100L)).thenReturn(List.of());
        when(approvalPolicy.findAvailableApproverRole(
                UserRole.ADMIN,
                approver.getRoles(),
                Set.of()
        )).thenReturn(Optional.of(UserRole.ADMIN));
        when(approvalMapper.toEntity(100L, 30L, UserRole.ADMIN, decision))
                .thenReturn(approval);
        when(approvalRepository.save(approval)).thenReturn(approval);
        when(approvalMapper.toResponse(approval)).thenReturn(response);

        RoleAssignmentApprovalResponse actual =
                service.recordDecision(1L, 100L, auth(30L), decision);

        assertSame(response, actual);
        assertFalse(roleRequest.isPending());
        assertEquals(UserStatus.ACTIVE, targetUser.getStatus());
    }

    @Test
    void recordDecision_whenFinalApprovalReceived_addsRoleAndApprovesRequest() {
        RoleAssignmentRequest roleRequest = roleRequest();
        User approver = user(40L, Set.of(UserRole.PRINCIPAL), UserStatus.ACTIVE);
        User targetUser = user(20L, Set.of(UserRole.TEACHER), UserStatus.ACTIVE);
        RoleApprovalDecisionRequest decision = decision(ApprovalDecision.APPROVED);
        RoleAssignmentApproval priorApproval =
                approval(UserRole.ADMIN, ApprovalDecision.APPROVED);
        RoleAssignmentApproval approval =
                approval(UserRole.PRINCIPAL, ApprovalDecision.APPROVED);
        RoleAssignmentApprovalResponse response = new RoleAssignmentApprovalResponse();

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(roleRequest));
        when(userRepository.findByOrganizationIdAndId(1L, 40L))
                .thenReturn(Optional.of(approver));
        when(approvalRepository.existsByRequestIdAndApproverUserId(100L, 40L))
                .thenReturn(false);
        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of(priorApproval));
        when(approvalPolicy.findAvailableApproverRole(
                UserRole.ADMIN,
                approver.getRoles(),
                Set.of(UserRole.ADMIN)
        )).thenReturn(Optional.of(UserRole.PRINCIPAL));
        when(approvalMapper.toEntity(100L, 40L, UserRole.PRINCIPAL, decision))
                .thenReturn(approval);
        when(approvalRepository.save(approval)).thenReturn(approval);
        when(approvalPolicy.getRequiredApproverRoles(UserRole.ADMIN))
                .thenReturn(Set.of(UserRole.ADMIN, UserRole.PRINCIPAL));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(targetUser));
        when(approvalMapper.toResponse(approval)).thenReturn(response);

        RoleAssignmentApprovalResponse actual =
                service.recordDecision(1L, 100L, auth(40L), decision);

        assertSame(response, actual);
        assertTrue(targetUser.hasRole(UserRole.ADMIN));
        assertFalse(roleRequest.isPending());
    }

    @Test
    void recordDecision_whenApprovalStillNeedsAnotherRole_doesNotAddTargetRole() {
        RoleAssignmentRequest roleRequest = roleRequest();
        User approver = user(30L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);
        RoleApprovalDecisionRequest decision = decision(ApprovalDecision.APPROVED);
        RoleAssignmentApproval approval =
                approval(UserRole.ADMIN, ApprovalDecision.APPROVED);

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(roleRequest));
        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));
        when(approvalRepository.existsByRequestIdAndApproverUserId(100L, 30L))
                .thenReturn(false);
        when(approvalRepository.findAllByRequestId(100L)).thenReturn(List.of());
        when(approvalPolicy.findAvailableApproverRole(
                UserRole.ADMIN,
                approver.getRoles(),
                Set.of()
        )).thenReturn(Optional.of(UserRole.ADMIN));
        when(approvalMapper.toEntity(100L, 30L, UserRole.ADMIN, decision))
                .thenReturn(approval);
        when(approvalRepository.save(approval)).thenReturn(approval);
        when(approvalPolicy.getRequiredApproverRoles(UserRole.ADMIN))
                .thenReturn(Set.of(UserRole.ADMIN, UserRole.PRINCIPAL));
        when(approvalMapper.toResponse(approval))
                .thenReturn(new RoleAssignmentApprovalResponse());

        service.recordDecision(1L, 100L, auth(30L), decision);

        assertTrue(roleRequest.isPending());
        verify(userRepository, never()).findByOrganizationIdAndId(1L, 20L);
    }

    @Test
    void getDecisionHistoryForApprover_whenApproverActive_returnsMappedPage() {
        User approver = user(30L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20);
        RoleAssignmentApproval approval =
                approval(UserRole.ADMIN, ApprovalDecision.APPROVED);
        RoleAssignmentApprovalResponse mapped =
                new RoleAssignmentApprovalResponse();

        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));
        when(approvalRepository.findDecisionHistoryForApprover(
                1L,
                30L,
                pageable
        )).thenReturn(new PageImpl<>(List.of(approval), pageable, 1));
        when(approvalMapper.toResponse(approval)).thenReturn(mapped);

        PageResponse<RoleAssignmentApprovalResponse> response =
                service.getDecisionHistoryForApprover(
                        1L,
                        30L,
                        pageable
                );

        assertEquals(List.of(mapped), response.content());
        assertEquals(1, response.totalElements());
        assertFalse(response.empty());
    }

    @Test
    void getDecisionHistoryForApprover_whenApproverInactive_throwsApprovalNotAllowedException() {
        User approver = user(30L, Set.of(UserRole.ADMIN), UserStatus.SUSPENDED);

        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));

        ApprovalNotAllowedException exception = assertThrows(
                ApprovalNotAllowedException.class,
                () -> service.getDecisionHistoryForApprover(
                        1L,
                        30L,
                        PageRequest.of(0, 20)
                )
        );

        assertEquals(
                "Only an active user can view decision history",
                exception.getMessage()
        );
        verify(approvalRepository, never())
                .findDecisionHistoryForApprover(anyLong(), anyLong(), any());
    }

    private static RoleApprovalDecisionRequest decision(
            ApprovalDecision decision
    ) {
        return new RoleApprovalDecisionRequest(decision, "Looks valid");
    }

    private static RoleAssignmentRequest roleRequest() {
        RoleAssignmentRequest request =
                new RoleAssignmentRequest(1L, 20L, UserRole.ADMIN, 10L, "Need admin");
        ReflectionTestUtils.setField(request, "id", 100L);
        return request;
    }

    private static RoleAssignmentApproval approval(
            UserRole approverRole,
            ApprovalDecision decision
    ) {
        return new RoleAssignmentApproval(
                100L,
                30L,
                approverRole,
                decision,
                "Looks valid"
        );
    }

    private static User user(
            Long id,
            Set<UserRole> roles,
            UserStatus status
    ) {
        User user = new User(1L, "user" + id, "User", roles);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        user.setStatus(status);
        return user;
    }

    private static AuthorizationContext auth(Long userId) {
        return new AuthorizationContext(
                userId,
                Set.of(PermissionCode.ROLE_ASSIGNMENT_APPROVE)
        );
    }
}
