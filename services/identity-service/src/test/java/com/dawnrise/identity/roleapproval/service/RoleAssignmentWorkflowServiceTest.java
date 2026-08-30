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
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentWorkflowServiceTest {

    @Mock
    private RoleAssignmentRequestRepository requestRepository;
    @Mock
    private RoleAssignmentApprovalRepository approvalRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SecurityAuditService auditService;

    private RoleAssignmentWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentWorkflowService(
                requestRepository,
                approvalRepository,
                userRepository,
                new RoleApprovalPolicy(),
                new RoleAssignmentApprovalMapper(),
                eventPublisher,
                auditService
        );
    }

    @Test
    void applyRequesterSignOff_whenAdminRequestsPrincipal_storesAdminApprovalAndRemainsPending() {
        RoleAssignmentRequest request =
                request(100L, 20L, UserRole.PRINCIPAL, 10L);
        User requester =
                user(10L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);
        RoleAssignmentApproval adminApproval =
                approval(UserRole.ADMIN, 10L);

        when(approvalRepository.save(any(RoleAssignmentApproval.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of(adminApproval));

        service.applyRequesterSignOff(1L, request, requester);

        ArgumentCaptor<RoleAssignmentApproval> captor =
                ArgumentCaptor.forClass(RoleAssignmentApproval.class);
        verify(approvalRepository).save(captor.capture());
        RoleAssignmentApproval signOff = captor.getValue();

        assertEquals(100L, signOff.getRequestId());
        assertEquals(10L, signOff.getApproverUserId());
        assertEquals(UserRole.ADMIN, signOff.getApproverRole());
        assertEquals(ApprovalDecision.APPROVED, signOff.getDecision());
        assertEquals(
                "System-recorded requester sign-off",
                signOff.getRemarks()
        );
        assertTrue(request.isPending());
        verify(auditService).record(
                eq(1L),
                eq(10L),
                eq(SecurityAuditAction.ROLE_ASSIGNMENT_DECISION),
                any(),
                eq("ROLE_ASSIGNMENT_REQUEST"),
                eq(100L),
                argThat(details ->
                        Boolean.TRUE.equals(details.get("requesterSignOff"))
                                && details.get("actingRequesterRole")
                                == UserRole.ADMIN)
        );
        verify(userRepository, never())
                .findByOrganizationIdAndId(1L, 20L);
    }

    @Test
    void applyRequesterSignOff_whenGoverningAuthorityRequestsPrincipal_storesGoverningAuthorityApproval() {
        RoleAssignmentRequest request =
                request(100L, 20L, UserRole.PRINCIPAL, 10L);
        User requester =
                user(10L, Set.of(UserRole.GOVERNING_AUTHORITY), UserStatus.ACTIVE);

        when(approvalRepository.save(any(RoleAssignmentApproval.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of(approval(UserRole.GOVERNING_AUTHORITY, 10L)));

        service.applyRequesterSignOff(1L, request, requester);

        ArgumentCaptor<RoleAssignmentApproval> captor =
                ArgumentCaptor.forClass(RoleAssignmentApproval.class);
        verify(approvalRepository).save(captor.capture());

        assertEquals(
                UserRole.GOVERNING_AUTHORITY,
                captor.getValue().getApproverRole()
        );
        assertTrue(request.isPending());
    }

    @Test
    void applyRequesterSignOff_whenRequesterHasMultipleRequiredRoles_satisfiesOnlyDeterministicFirstRole() {
        RoleAssignmentRequest request =
                request(100L, 20L, UserRole.ADMIN, 10L);
        User requester =
                user(
                        10L,
                        Set.of(UserRole.ADMIN, UserRole.GOVERNING_AUTHORITY),
                        UserStatus.ACTIVE
                );

        when(approvalRepository.save(any(RoleAssignmentApproval.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of(approval(UserRole.ADMIN, 10L)));

        service.applyRequesterSignOff(1L, request, requester);

        ArgumentCaptor<RoleAssignmentApproval> captor =
                ArgumentCaptor.forClass(RoleAssignmentApproval.class);
        verify(approvalRepository, times(1)).save(captor.capture());

        assertEquals(UserRole.ADMIN, captor.getValue().getApproverRole());
        assertTrue(request.isPending());
    }

    @Test
    void applyRequesterSignOff_whenRequesterHasNoRequiredRole_createsNoApproval() {
        RoleAssignmentRequest request =
                request(100L, 20L, UserRole.ADMIN, 10L);
        User requester =
                user(10L, Set.of(UserRole.HR), UserStatus.ACTIVE);

        assertTrue(
                service.applyRequesterSignOff(1L, request, requester)
                        .isEmpty()
        );

        verifyNoInteractions(approvalRepository, userRepository, auditService);
        assertTrue(request.isPending());
    }

    @Test
    void applyRequesterSignOff_whenSingleRequiredRoleSatisfied_assignsRoleAndQueuesActivation() {
        RoleAssignmentRequest request =
                request(100L, 20L, UserRole.VICE_PRINCIPAL, 10L);
        User requester =
                user(10L, Set.of(UserRole.PRINCIPAL), UserStatus.ACTIVE);
        User target =
                user(20L, Set.of(), UserStatus.PENDING_APPROVAL);

        when(approvalRepository.save(any(RoleAssignmentApproval.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of(approval(UserRole.PRINCIPAL, 10L)));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(requestRepository.existsByOrganizationIdAndUserIdAndStatusAndIdNot(
                1L,
                20L,
                ApprovalStatus.PENDING,
                100L
        )).thenReturn(false);

        service.applyRequesterSignOff(1L, request, requester);

        assertTrue(target.hasRole(UserRole.VICE_PRINCIPAL));
        assertFalse(request.isPending());
        assertEquals(UserStatus.PENDING_ACTIVATION, target.getStatus());
        verify(eventPublisher).publishEvent(
                new UserActivationRequestedEvent(20L)
        );
    }

    @Test
    void finalizeIfFullyApproved_whenAnotherRequestPending_doesNotActivateEarly() {
        RoleAssignmentRequest request =
                request(100L, 20L, UserRole.ADMIN, 10L);
        User target =
                user(20L, Set.of(), UserStatus.PENDING_APPROVAL);

        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of(
                        approval(UserRole.ADMIN, 10L),
                        approval(UserRole.GOVERNING_AUTHORITY, 30L)
                ));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(requestRepository.existsByOrganizationIdAndUserIdAndStatusAndIdNot(
                1L,
                20L,
                ApprovalStatus.PENDING,
                100L
        )).thenReturn(true);

        assertTrue(service.finalizeIfFullyApproved(1L, request, 30L));

        assertTrue(target.hasRole(UserRole.ADMIN));
        assertFalse(request.isPending());
        assertEquals(UserStatus.PENDING_APPROVAL, target.getStatus());
        verify(eventPublisher, never())
                .publishEvent(any(UserActivationRequestedEvent.class));
    }

    @Test
    void finalizeIfFullyApproved_whenLastRequestCompletes_queuesOneActivationEvent() {
        RoleAssignmentRequest request =
                request(101L, 20L, UserRole.PRINCIPAL, 10L);
        User target =
                user(20L, Set.of(UserRole.ADMIN), UserStatus.PENDING_APPROVAL);

        when(approvalRepository.findAllByRequestId(101L))
                .thenReturn(List.of(
                        approval(UserRole.ADMIN, 10L),
                        approval(UserRole.GOVERNING_AUTHORITY, 30L)
                ));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(requestRepository.existsByOrganizationIdAndUserIdAndStatusAndIdNot(
                1L,
                20L,
                ApprovalStatus.PENDING,
                101L
        )).thenReturn(false);

        assertTrue(service.finalizeIfFullyApproved(1L, request, 30L));

        assertTrue(target.hasRole(UserRole.PRINCIPAL));
        assertEquals(UserStatus.PENDING_ACTIVATION, target.getStatus());
        verify(eventPublisher, times(1)).publishEvent(
                new UserActivationRequestedEvent(20L)
        );
    }

    private static RoleAssignmentRequest request(
            Long requestId,
            Long targetUserId,
            UserRole requestedRole,
            Long requesterUserId
    ) {
        RoleAssignmentRequest request =
                new RoleAssignmentRequest(
                        1L,
                        targetUserId,
                        requestedRole,
                        requesterUserId,
                        "Need role"
                );
        ReflectionTestUtils.setField(request, "id", requestId);
        return request;
    }

    private static RoleAssignmentApproval approval(
            UserRole approverRole,
            Long approverUserId
    ) {
        return new RoleAssignmentApproval(
                100L,
                approverUserId,
                approverRole,
                ApprovalDecision.APPROVED,
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
}
