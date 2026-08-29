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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentRequestServiceImplTest {

    @Mock
    private RoleAssignmentRequestRepository requestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleApprovalPolicy approvalPolicy;
    @Mock
    private RoleAssignmentRequestMapper requestMapper;
    @Mock
    private RoleAssignmentApprovalRepository approvalRepository;
    @Mock
    private RoleAssignmentApprovalMapper approvalMapper;
    @Mock
    private SecurityAuditService auditService;

    private RoleAssignmentRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentRequestServiceImpl(
                requestRepository,
                approvalRepository,
                userRepository,
                approvalPolicy,
                requestMapper,
                approvalMapper,
                auditService
        );
    }

    @Test
    void createRequest_whenRequesterMissing_throwsResourceNotFoundException() {
        CreateRoleAssignmentRequest request = request(20L, UserRole.ADMIN);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.createRequest(1L, auth(10L), request)
        );
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequest_whenRequesterInactive_throwsInvalidRoleRequestException() {
        CreateRoleAssignmentRequest request = request(20L, UserRole.ADMIN);
        User requester = user(10L, Set.of(UserRole.HR), UserStatus.SUSPENDED);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));

        InvalidRoleRequestException exception = assertThrows(
                InvalidRoleRequestException.class,
                () -> service.createRequest(1L, auth(10L), request)
        );

        assertEquals(
                "Only an active user can submit a role request",
                exception.getMessage()
        );
    }

    @Test
    void createRequest_whenPolicyRejectsRequest_throwsInvalidRoleRequestException() {
        CreateRoleAssignmentRequest request = request(20L, UserRole.ADMIN);
        User requester = user(10L, Set.of(UserRole.TEACHER), UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(approvalPolicy.canRequestApproval(
                requester.getRoles(),
                UserRole.ADMIN
        )).thenReturn(false);

        InvalidRoleRequestException exception = assertThrows(
                InvalidRoleRequestException.class,
                () -> service.createRequest(1L, auth(10L), request)
        );

        assertEquals(
                "You are not allowed to request this role",
                exception.getMessage()
        );
        verify(userRepository, never()).findByOrganizationIdAndId(1L, 20L);
    }

    @Test
    void createRequest_whenTargetAlreadyHasRole_throwsInvalidRoleRequestException() {
        CreateRoleAssignmentRequest request = request(20L, UserRole.ADMIN);
        User requester = user(10L, Set.of(UserRole.HR), UserStatus.ACTIVE);
        User target = user(20L, Set.of(UserRole.ADMIN), UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(approvalPolicy.canRequestApproval(
                requester.getRoles(),
                UserRole.ADMIN
        )).thenReturn(true);
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));

        InvalidRoleRequestException exception = assertThrows(
                InvalidRoleRequestException.class,
                () -> service.createRequest(1L, auth(10L), request)
        );

        assertEquals(
                "The user already has the requested role",
                exception.getMessage()
        );
    }

    @Test
    void createRequest_whenPendingRequestExists_throwsDuplicateResourceException() {
        CreateRoleAssignmentRequest request = request(20L, UserRole.ADMIN);
        User requester = user(10L, Set.of(UserRole.HR), UserStatus.ACTIVE);
        User target = user(20L, Set.of(UserRole.TEACHER), UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(approvalPolicy.canRequestApproval(
                requester.getRoles(),
                UserRole.ADMIN
        )).thenReturn(true);
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(requestRepository
                .existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                        1L,
                        20L,
                        UserRole.ADMIN,
                        ApprovalStatus.PENDING
                )).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> service.createRequest(1L, auth(10L), request)
        );

        assertEquals(
                "A pending request already exists for this user and role",
                exception.getMessage()
        );
    }

    @Test
    void createRequest_whenValid_savesAndReturnsResponse() {
        CreateRoleAssignmentRequest request = request(20L, UserRole.ADMIN);
        User requester = user(10L, Set.of(UserRole.HR), UserStatus.ACTIVE);
        User target = user(20L, Set.of(UserRole.TEACHER), UserStatus.ACTIVE);
        RoleAssignmentRequest entity =
                new RoleAssignmentRequest(1L, 20L, UserRole.ADMIN, 10L, "Need admin");
        RoleAssignmentRequestResponse response =
                new RoleAssignmentRequestResponse();

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(approvalPolicy.canRequestApproval(
                requester.getRoles(),
                UserRole.ADMIN
        )).thenReturn(true);
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(requestMapper.toEntity(1L, 10L, request)).thenReturn(entity);
        when(requestRepository.save(entity)).thenReturn(entity);
        when(requestMapper.toResponse(entity)).thenReturn(response);

        RoleAssignmentRequestResponse actual =
                service.createRequest(1L, auth(10L), request);

        assertSame(response, actual);
        verify(requestRepository).save(entity);
    }

    @Test
    void getActionableRequests_whenApproverHasNoReviewableRoles_returnsEmptyPage() {
        User approver = user(30L, Set.of(UserRole.TEACHER), UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20);

        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));
        when(approvalPolicy.getReviewableRoles(approver.getRoles()))
                .thenReturn(Set.of());

        PageResponse<RoleAssignmentRequestResponse> response =
                service.getActionableRequestsForApprover(1L, 30L, pageable);

        assertTrue(response.empty());
        verify(requestRepository, never()).findActionableRequestsForApprover(
                anyLong(),
                any(),
                anySet(),
                anyLong(),
                any()
        );
    }

    @Test
    void getActionableRequests_whenApproverInactive_throwsApprovalNotAllowedException() {
        User approver = user(30L, Set.of(UserRole.PRINCIPAL), UserStatus.SUSPENDED);

        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));

        assertThrows(
                ApprovalNotAllowedException.class,
                () -> service.getActionableRequestsForApprover(
                        1L,
                        30L,
                        PageRequest.of(0, 20)
                )
        );
    }

    @Test
    void getActionableRequests_whenReviewableRolesExist_returnsMappedPage() {
        User approver = user(30L, Set.of(UserRole.PRINCIPAL), UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20);
        RoleAssignmentRequest entity =
                new RoleAssignmentRequest(1L, 20L, UserRole.ADMIN, 10L, "Need admin");
        RoleAssignmentRequestResponse mapped =
                new RoleAssignmentRequestResponse();

        when(userRepository.findByOrganizationIdAndId(1L, 30L))
                .thenReturn(Optional.of(approver));
        when(approvalPolicy.getReviewableRoles(approver.getRoles()))
                .thenReturn(Set.of(UserRole.ADMIN));
        when(requestRepository.findActionableRequestsForApprover(
                1L,
                ApprovalStatus.PENDING,
                Set.of(UserRole.ADMIN),
                30L,
                pageable
        )).thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
        when(requestMapper.toResponse(entity)).thenReturn(mapped);

        PageResponse<RoleAssignmentRequestResponse> response =
                service.getActionableRequestsForApprover(1L, 30L, pageable);

        assertEquals(List.of(mapped), response.content());
        assertFalse(response.empty());
    }

    private static CreateRoleAssignmentRequest request(
            Long userId,
            UserRole role
    ) {
        return new CreateRoleAssignmentRequest(userId, role, "Need admin");
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
                Set.of(
                        PermissionCode.ROLE_ASSIGNMENT_REQUEST_CREATE,
                        PermissionCode.ROLE_ASSIGNMENT_REQUEST_CANCEL
                )
        );
    }
}
