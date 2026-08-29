package com.dawnrise.identity.roleremoval.service;

import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.roleapproval.dto.RoleApprovalDecisionRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalDecision;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleapproval.exception.InvalidRoleRequestException;
import com.dawnrise.identity.roleapproval.policy.RoleApprovalPolicy;
import com.dawnrise.identity.roleremoval.dto.CreateRoleRemovalRequest;
import com.dawnrise.identity.roleremoval.entity.RoleRemovalApproval;
import com.dawnrise.identity.roleremoval.entity.RoleRemovalRequest;
import com.dawnrise.identity.roleremoval.exception.ProtectedRoleRemovalException;
import com.dawnrise.identity.roleremoval.mapper.RoleRemovalApprovalMapper;
import com.dawnrise.identity.roleremoval.mapper.RoleRemovalRequestMapper;
import com.dawnrise.identity.roleremoval.policy.ProtectedRoleRemovalPolicy;
import com.dawnrise.identity.roleremoval.policy.RoleRemovalApprovalPolicy;
import com.dawnrise.identity.roleremoval.repository.RoleRemovalApprovalRepository;
import com.dawnrise.identity.roleremoval.repository.RoleRemovalRequestRepository;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleRemovalWorkflowServiceTest {

    @Mock
    private RoleRemovalRequestRepository requestRepository;
    @Mock
    private RoleRemovalApprovalRepository approvalRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private SecurityAuditService auditService;

    private RoleRemovalRequestServiceImpl requestService;
    private RoleRemovalApprovalServiceImpl approvalService;
    private RoleApprovalPolicy roleApprovalPolicy;

    @BeforeEach
    void setUp() {
        roleApprovalPolicy = new RoleApprovalPolicy();
        RoleRemovalApprovalPolicy removalPolicy =
                new RoleRemovalApprovalPolicy(roleApprovalPolicy);
        ProtectedRoleRemovalPolicy protectedPolicy =
                new ProtectedRoleRemovalPolicy(userRepository);
        RoleRemovalRequestMapper requestMapper =
                new RoleRemovalRequestMapper(
                        roleApprovalPolicy,
                        approvalRepository
                );
        RoleRemovalApprovalMapper approvalMapper =
                new RoleRemovalApprovalMapper();

        requestService = new RoleRemovalRequestServiceImpl(
                requestRepository,
                approvalRepository,
                userRepository,
                removalPolicy,
                protectedPolicy,
                requestMapper,
                approvalMapper,
                auditService
        );
        approvalService = new RoleRemovalApprovalServiceImpl(
                requestRepository,
                approvalRepository,
                userRepository,
                removalPolicy,
                protectedPolicy,
                approvalMapper,
                refreshTokenService,
                auditService
        );
    }

    @Test
    void createRequest_whenRoutineRole_rejectsWorkflow() {
        User requester = user(10L, Set.of(UserRole.HR), UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));

        InvalidRoleRequestException exception = assertThrows(
                InvalidRoleRequestException.class,
                () -> requestService.createRequest(
                        1L,
                        auth(10L, PermissionCode.ROLE_REMOVAL_REQUEST_CREATE),
                        new CreateRoleRemovalRequest(
                                20L,
                                UserRole.TEACHER,
                                "Routine cleanup"
                        )
                )
        );

        assertEquals(
                "Routine role removal must use the routine role update endpoint",
                exception.getMessage()
        );
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequest_whenTargetDoesNotHaveRole_rejectsRequest() {
        User requester = user(10L, Set.of(UserRole.HR), UserStatus.ACTIVE);
        User target = user(20L, Set.of(UserRole.TEACHER), UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));

        assertThrows(
                InvalidRoleRequestException.class,
                () -> requestService.createRequest(
                        1L,
                        auth(10L, PermissionCode.ROLE_REMOVAL_REQUEST_CREATE),
                        new CreateRoleRemovalRequest(
                                20L,
                                UserRole.ADMIN,
                                "Remove admin"
                        )
                )
        );
    }

    @Test
    void createRequest_whenDuplicatePendingRequest_throwsConflict() {
        User requester = user(10L, Set.of(UserRole.HR), UserStatus.ACTIVE);
        User target = user(20L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(userRepository.countByOrganizationIdAndStatusAndRole(
                1L,
                UserStatus.ACTIVE,
                UserRole.ADMIN
        )).thenReturn(2L);
        when(requestRepository.existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                1L,
                20L,
                UserRole.ADMIN,
                ApprovalStatus.PENDING
        )).thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> requestService.createRequest(
                        1L,
                        auth(10L, PermissionCode.ROLE_REMOVAL_REQUEST_CREATE),
                        new CreateRoleRemovalRequest(
                                20L,
                                UserRole.ADMIN,
                                "Remove admin"
                        )
                )
        );
    }

    @Test
    void createRequest_whenLastActiveAdmin_rejectsProtectedRemoval() {
        User requester = user(10L, Set.of(UserRole.HR), UserStatus.ACTIVE);
        User target = user(20L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(userRepository.countByOrganizationIdAndStatusAndRole(
                1L,
                UserStatus.ACTIVE,
                UserRole.ADMIN
        )).thenReturn(1L);

        assertThrows(
                ProtectedRoleRemovalException.class,
                () -> requestService.createRequest(
                        1L,
                        auth(10L, PermissionCode.ROLE_REMOVAL_REQUEST_CREATE),
                        new CreateRoleRemovalRequest(
                                20L,
                                UserRole.ADMIN,
                                "Remove last admin"
                        )
                )
        );
    }

    @Test
    void recordDecision_whenFinalApproval_removesOnlyRequestedRoleAndRevokesRefreshTokens() {
        RoleRemovalRequest request = removalRequest();
        User approver = user(
                40L,
                Set.of(UserRole.GOVERNING_AUTHORITY),
                UserStatus.ACTIVE
        );
        User target = user(
                20L,
                Set.of(UserRole.ADMIN, UserRole.TEACHER),
                UserStatus.ACTIVE
        );
        RoleRemovalApproval priorApproval = approval(
                UserRole.ADMIN,
                ApprovalDecision.APPROVED
        );

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(request));
        when(userRepository.findByOrganizationIdAndId(1L, 40L))
                .thenReturn(Optional.of(approver));
        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of(priorApproval));
        when(approvalRepository.save(any(RoleRemovalApproval.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(userRepository.countByOrganizationIdAndStatusAndRole(
                1L,
                UserStatus.ACTIVE,
                UserRole.ADMIN
        )).thenReturn(2L);

        approvalService.recordDecision(
                1L,
                100L,
                auth(40L, PermissionCode.ROLE_REMOVAL_APPROVE),
                new RoleApprovalDecisionRequest(
                        ApprovalDecision.APPROVED,
                        "Approved"
                )
        );

        assertFalse(target.hasRole(UserRole.ADMIN));
        assertTrue(target.hasRole(UserRole.TEACHER));
        assertFalse(request.isPending());
        verify(refreshTokenService).revokeAllForUser(20L);
    }

    @Test
    void recordDecision_whenOnlyOneApproval_doesNotRemoveRole() {
        RoleRemovalRequest request = removalRequest();
        User approver = user(30L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(request));
        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));
        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of());
        when(approvalRepository.save(any(RoleRemovalApproval.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        approvalService.recordDecision(
                1L,
                100L,
                auth(30L, PermissionCode.ROLE_REMOVAL_APPROVE),
                new RoleApprovalDecisionRequest(
                        ApprovalDecision.APPROVED,
                        "Approved"
                )
        );

        assertTrue(request.isPending());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void recordDecision_whenPermissionButIneligibleRole_deniesApproval() {
        RoleRemovalRequest request = removalRequest();
        User approver = user(30L, Set.of(UserRole.TEACHER), UserStatus.ACTIVE);

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(request));
        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));
        when(approvalRepository.findAllByRequestId(100L))
                .thenReturn(List.of());

        assertThrows(
                ApprovalNotAllowedException.class,
                () -> approvalService.recordDecision(
                        1L,
                        100L,
                        auth(30L, PermissionCode.ROLE_REMOVAL_APPROVE),
                        new RoleApprovalDecisionRequest(
                                ApprovalDecision.APPROVED,
                                "Approved"
                        )
                )
        );
    }

    @Test
    void cancelRequest_whenRequester_cancelsWithoutRemovingRole() {
        RoleRemovalRequest request = removalRequest();

        when(requestRepository.findByIdAndOrganizationId(100L, 1L))
                .thenReturn(Optional.of(request));

        requestService.cancelRequest(
                1L,
                100L,
                auth(10L, PermissionCode.ROLE_REMOVAL_REQUEST_CANCEL)
        );

        assertFalse(request.isPending());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    private AuthorizationContext auth(
            Long userId,
            PermissionCode permission
    ) {
        return new AuthorizationContext(userId, Set.of(permission));
    }

    private User user(Long id, Set<UserRole> roles, UserStatus status) {
        User user = new User(1L, "user" + id, "User", roles);
        ReflectionTestUtils.setField(user, "id", id);
        user.setStatus(status);
        return user;
    }

    private RoleRemovalRequest removalRequest() {
        RoleRemovalRequest request = new RoleRemovalRequest(
                1L,
                20L,
                UserRole.ADMIN,
                10L,
                "Remove admin"
        );
        ReflectionTestUtils.setField(request, "id", 100L);
        return request;
    }

    private RoleRemovalApproval approval(
            UserRole approverRole,
            ApprovalDecision decision
    ) {
        return new RoleRemovalApproval(
                100L,
                30L,
                approverRole,
                decision,
                "ok"
        );
    }
}
