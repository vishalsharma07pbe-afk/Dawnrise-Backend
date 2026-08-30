package com.dawnrise.identity.user.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.auth.activation.event.UserActivationRequestedEvent;
import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.organization.repository.OrganizationRepository;
import com.dawnrise.identity.organization.entity.Organization;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.roleapproval.dto.CreateRoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleapproval.exception.InvalidRoleRequestException;
import com.dawnrise.identity.roleapproval.mapper.RoleAssignmentRequestMapper;
import com.dawnrise.identity.roleapproval.policy.RoleApprovalPolicy;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentRequestRepository;
import com.dawnrise.identity.roleapproval.service.RoleAssignmentWorkflowService;
import com.dawnrise.identity.roleremoval.repository.RoleRemovalRequestRepository;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.user.dto.*;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.user.exception.InvalidUserStatusTransitionException;
import com.dawnrise.identity.user.mapper.UserMapper;
import com.dawnrise.identity.user.policy.UserStatusAuthorizationPolicy;
import com.dawnrise.identity.user.policy.UserStatusTransitionPolicy;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleApprovalPolicy roleApprovalPolicy;
    @Mock
    private RoleAssignmentRequestRepository roleRequestRepository;
    @Mock
    private RoleRemovalRequestRepository roleRemovalRequestRepository;
    @Mock
    private RoleAssignmentRequestMapper roleRequestMapper;
    @Mock
    private RoleAssignmentWorkflowService roleAssignmentWorkflowService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserStatusTransitionPolicy statusTransitionPolicy;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private SecurityAuditService auditService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userRepository,
                organizationRepository,
                userMapper,
                roleApprovalPolicy,
                roleRequestRepository,
                roleRemovalRequestRepository,
                roleRequestMapper,
                roleAssignmentWorkflowService,
                eventPublisher,
                statusTransitionPolicy,
                new UserStatusAuthorizationPolicy(statusTransitionPolicy),
                refreshTokenService,
                auditService
        );

        lenient()
                .when(roleApprovalPolicy.getSensitiveRoles(anySet()))
                .thenReturn(Set.of());
    }

    @Test
    void createUser_whenUsernameExists_throwsDuplicateResourceException() {
        CreateUserRequest request = new CreateUserRequest();
        request.setOrganizationId(1L);
        request.setUsername("teacher01");
        User creator = user(99L, 1L, "admin01", "admin@dawnrise.com");
        creator.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(creator));
        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "teacher01"
        )).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.createUser(1L, auth(99L), request)
        );

        assertEquals(
                "Username already exists in this organization: teacher01",
                exception.getMessage()
        );

        verify(userRepository).existsByOrganizationIdAndUsername(
                1L,
                "teacher01"
        );

        verify(userRepository, never())
                .save(any(User.class));
    }

    @Test
    void createUser_whenEmailExists_throwsDuplicateResourceException() {
        CreateUserRequest request = new CreateUserRequest();
        request.setOrganizationId(1L);
        request.setUsername("teacher01");
        request.setEmail("teacher@dawnrise.com");
        User creator = user(99L, 1L, "admin01", "admin@dawnrise.com");
        creator.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(creator));
        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "teacher01"
        )).thenReturn(false);

        when(userRepository.existsByOrganizationIdAndEmail(
                1L,
                "teacher@dawnrise.com"
        )).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.createUser(1L, auth(99L), request)
        );

        assertEquals(
                "Email already exists in this organization: "
                        + "teacher@dawnrise.com",
                exception.getMessage()
        );

        verify(userRepository).existsByOrganizationIdAndUsername(
                1L,
                "teacher01"
        );

        verify(userRepository).existsByOrganizationIdAndEmail(
                1L,
                "teacher@dawnrise.com"
        );

        verify(userRepository, never())
                .save(any(User.class));
    }

    @Test
    void createUser_whenValid_savesAndReturnsResponse() {
        CreateUserRequest request = new CreateUserRequest();
        request.setOrganizationId(1L);
        request.setUsername("teacher01");
        request.setFirstName("Rahul");
        request.setEmail("rahul@dawnrise.com");
        request.setRoles(Set.of(UserRole.TEACHER));

        User creator = user(99L, 1L, "admin01", "admin@dawnrise.com");
        creator.setStatus(UserStatus.ACTIVE);
        User user = new User();
        user.setOrganizationId(1L);
        user.setUsername("teacher01");
        user.setFirstName("Rahul");
        user.setEmail("rahul@dawnrise.com");

        User savedUser = new User();
        savedUser.setOrganizationId(1L);
        savedUser.setUsername("teacher01");
        savedUser.setFirstName("Rahul");
        savedUser.setEmail("rahul@dawnrise.com");
        savedUser.setStatus(UserStatus.PENDING_ACTIVATION);
        ReflectionTestUtils.setField(savedUser, "id", 10L);

        UserResponse expectedResponse = new UserResponse();
        expectedResponse.setId(10L);
        expectedResponse.setOrganizationId(1L);
        expectedResponse.setUsername("teacher01");
        expectedResponse.setFirstName("Rahul");
        expectedResponse.setEmail("rahul@dawnrise.com");

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(creator));
        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "teacher01"
        )).thenReturn(false);

        when(userRepository.existsByOrganizationIdAndEmail(
                1L,
                "rahul@dawnrise.com"
        )).thenReturn(false);
        when(roleApprovalPolicy.getRoutineRoles(Set.of(UserRole.TEACHER)))
                .thenReturn(Set.of(UserRole.TEACHER));
        when(roleApprovalPolicy.getSensitiveRoles(Set.of(UserRole.TEACHER)))
                .thenReturn(Set.of());

        when(userMapper.toEntity(request))
                .thenReturn(user);

        when(userRepository.save(user))
                .thenReturn(savedUser);

        when(userMapper.toResponse(savedUser))
                .thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.createUser(1L, auth(99L), request);

        assertSame(expectedResponse, actualResponse);

        assertNull(user.getPasswordHash());
        assertEquals(UserStatus.PENDING_ACTIVATION, user.getStatus());

        verify(userRepository)
                .existsByOrganizationIdAndUsername(
                        1L,
                        "teacher01");
        verify(userRepository)
                .existsByOrganizationIdAndEmail(
                        1L,
                        "rahul@dawnrise.com");
        verify(userMapper).toEntity(request);
        verify(userRepository).save(user);
        verify(eventPublisher).publishEvent(
                new UserActivationRequestedEvent(10L)
        );
        verify(userMapper).toResponse(savedUser);
    }

    @Test
    void createUser_whenFirstAdminRequestedByGoverningAuthority_bootstrapsAdmin() {
        CreateUserRequest request = new CreateUserRequest();
        request.setOrganizationId(1L);
        request.setUsername("admin01");
        request.setFirstName("Initial");
        request.setLastName("Admin");
        request.setEmail("admin@dawnrise.com");
        request.setRoles(Set.of(UserRole.ADMIN));

        User creator = user(
                99L,
                1L,
                "governing01",
                "governing@dawnrise.com"
        );
        creator.setStatus(UserStatus.ACTIVE);
        creator.setRoles(Set.of(UserRole.GOVERNING_AUTHORITY));

        User invitedAdmin = new User();
        invitedAdmin.setUsername("admin01");
        invitedAdmin.setFirstName("Initial");
        invitedAdmin.setLastName("Admin");
        invitedAdmin.setEmail("admin@dawnrise.com");
        ReflectionTestUtils.setField(invitedAdmin, "id", 10L);

        UserResponse expectedResponse =
                response(10L, 1L, "admin01");

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(creator));

        when(organizationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(mock(Organization.class)));

        when(userRepository.countByOrganizationIdAndRole(
                1L,
                UserRole.ADMIN
        )).thenReturn(0L);

        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "admin01"
        )).thenReturn(false);

        when(userRepository.existsByOrganizationIdAndEmail(
                1L,
                "admin@dawnrise.com"
        )).thenReturn(false);

        when(userMapper.toEntity(request))
                .thenReturn(invitedAdmin);

        when(userRepository.save(invitedAdmin))
                .thenReturn(invitedAdmin);

        when(userMapper.toResponse(invitedAdmin))
                .thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.createUser(
                        1L,
                        auth(99L),
                        request
                );

        assertSame(expectedResponse, actualResponse);
        assertEquals(
                Set.of(UserRole.ADMIN),
                invitedAdmin.getRoles()
        );
        assertEquals(
                UserStatus.PENDING_ACTIVATION,
                invitedAdmin.getStatus()
        );

        verify(organizationRepository)
                .findByIdForUpdate(1L);

        verify(userRepository)
                .countByOrganizationIdAndRole(
                        1L,
                        UserRole.ADMIN
                );

        verify(roleRequestRepository, never())
                .save(any(RoleAssignmentRequest.class));

        verify(eventPublisher).publishEvent(
                new UserActivationRequestedEvent(10L)
        );
    }

    @Test
    void createUser_whenAdminAlreadyExists_usesApprovalWorkflow() {
        CreateUserRequest request = new CreateUserRequest();
        request.setOrganizationId(1L);
        request.setUsername("admin02");
        request.setFirstName("Second");
        request.setLastName("Admin");
        request.setEmail("admin02@dawnrise.com");
        request.setRoles(Set.of(UserRole.ADMIN));

        User creator = user(
                99L,
                1L,
                "governing01",
                "governing@dawnrise.com"
        );
        creator.setStatus(UserStatus.ACTIVE);
        creator.setRoles(Set.of(UserRole.GOVERNING_AUTHORITY));

        User candidate = new User();
        candidate.setUsername("admin02");
        candidate.setEmail("admin02@dawnrise.com");
        ReflectionTestUtils.setField(candidate, "id", 11L);

        RoleAssignmentRequest roleRequest =
                mock(RoleAssignmentRequest.class);

        UserResponse expectedResponse =
                response(11L, 1L, "admin02");

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(creator));

        when(organizationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(mock(Organization.class)));

        when(userRepository.countByOrganizationIdAndRole(
                1L,
                UserRole.ADMIN
        )).thenReturn(1L);

        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "admin02"
        )).thenReturn(false);

        when(userRepository.existsByOrganizationIdAndEmail(
                1L,
                "admin02@dawnrise.com"
        )).thenReturn(false);

        when(roleApprovalPolicy.getRoutineRoles(
                Set.of(UserRole.ADMIN)
        )).thenReturn(Set.of());

        when(roleApprovalPolicy.getSensitiveRoles(
                Set.of(UserRole.ADMIN)
        )).thenReturn(Set.of(UserRole.ADMIN));

        when(roleApprovalPolicy.canRequestApproval(
                Set.of(UserRole.GOVERNING_AUTHORITY),
                UserRole.ADMIN
        )).thenReturn(true);

        when(userMapper.toEntity(request))
                .thenReturn(candidate);

        when(userRepository.save(candidate))
                .thenReturn(candidate);

        when(roleRequestMapper.toEntity(
                eq(1L),
                eq(99L),
                any(CreateRoleAssignmentRequest.class)
        )).thenReturn(roleRequest);
        when(roleRequestRepository.save(roleRequest))
                .thenReturn(roleRequest);

        when(userMapper.toResponse(candidate))
                .thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.createUser(
                        1L,
                        auth(99L),
                        request
                );

        assertSame(expectedResponse, actualResponse);
        assertTrue(candidate.getRoles().isEmpty());
        assertEquals(
                UserStatus.PENDING_APPROVAL,
                candidate.getStatus()
        );

        verify(roleRequestRepository).save(roleRequest);
        verify(roleAssignmentWorkflowService).applyRequesterSignOff(
                1L,
                roleRequest,
                creator
        );

        verify(eventPublisher, never()).publishEvent(
                any(UserActivationRequestedEvent.class)
        );
    }

    @Test
    void createUser_whenEmailIsBlank_doesNotCheckEmailUniqueness() {
        CreateUserRequest request = new CreateUserRequest();
        request.setOrganizationId(1L);
        request.setUsername("teacher01");
        request.setEmail(" ");
        request.setRoles(Set.of(UserRole.TEACHER));

        User creator = user(99L, 1L, "admin01", "admin@dawnrise.com");
        creator.setStatus(UserStatus.ACTIVE);
        User user = user(1L, "teacher01", "old@dawnrise.com");
        UserResponse expectedResponse = response(10L, 1L, "teacher01");

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(creator));
        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "teacher01"
        )).thenReturn(false);
        when(roleApprovalPolicy.getRoutineRoles(Set.of(UserRole.TEACHER)))
                .thenReturn(Set.of(UserRole.TEACHER));
        when(roleApprovalPolicy.getSensitiveRoles(Set.of(UserRole.TEACHER)))
                .thenReturn(Set.of());
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse actualResponse = userService.createUser(1L, auth(99L), request);

        assertSame(expectedResponse, actualResponse);
        assertNull(user.getPasswordHash());

        verify(userRepository, never())
                .existsByOrganizationIdAndEmail(anyLong(), anyString());
    }

    @Test
    void getUserById_whenFound_returnsResponse() {
        User user = user(1L, "teacher01", "teacher@dawnrise.com");
        UserResponse expectedResponse = response(10L, 1L, "teacher01");

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse actualResponse = userService.getUserById(1L, 10L);

        assertSame(expectedResponse, actualResponse);
        verify(userRepository).findByOrganizationIdAndId(1L, 10L);
        verify(userMapper).toResponse(user);
    }

    @Test
    void getUserById_whenMissing_throwsResourceNotFoundException() {
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserById(1L, 10L)
        );

        assertEquals(
                "User not found with ID: 10 in organization: 1",
                exception.getMessage()
        );
        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void getAllUsersByOrganization_whenUsersExist_returnsPageResponse() {
        Pageable pageable = PageRequest.of(1, 2);
        User firstUser = user(1L, "teacher01", "teacher@dawnrise.com");
        User secondUser = user(1L, "student01", "student@dawnrise.com");
        UserResponse firstResponse = response(10L, 1L, "teacher01");
        UserResponse secondResponse = response(11L, 1L, "student01");

        when(userRepository.findAllByOrganizationId(1L, pageable))
                .thenReturn(new PageImpl<>(
                        List.of(firstUser, secondUser),
                        pageable,
                        5
                ));
        when(userMapper.toResponse(firstUser)).thenReturn(firstResponse);
        when(userMapper.toResponse(secondUser)).thenReturn(secondResponse);

        PageResponse<UserResponse> actualResponse =
                userService.getAllUsersByOrganization(1L, pageable);

        assertEquals(List.of(firstResponse, secondResponse),
                actualResponse.content());
        assertEquals(1, actualResponse.pageNumber());
        assertEquals(2, actualResponse.pageSize());
        assertEquals(5, actualResponse.totalElements());
        assertEquals(3, actualResponse.totalPages());
        assertFalse(actualResponse.first());
        assertFalse(actualResponse.last());
        assertFalse(actualResponse.empty());
    }

    @Test
    void updateUserProfile_whenUserMissing_throwsResourceNotFoundException() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.updateUserProfile(1L, 10L, request)
        );

        assertEquals(
                "User not found with ID: 10 in organization: 1",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserProfile_whenNewEmailExists_throwsDuplicateResourceException() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setEmail("new@dawnrise.com");
        User user = user(1L, "teacher01", "old@dawnrise.com");

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByOrganizationIdAndEmail(
                1L,
                "new@dawnrise.com"
        )).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.updateUserProfile(1L, 10L, request)
        );

        assertEquals(
                "Email already exists in this organization: new@dawnrise.com",
                exception.getMessage()
        );
        verify(userMapper, never())
                .updateProfile(any(UpdateUserProfileRequest.class),
                        any(User.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserProfile_whenEmailUnchangedIgnoresCase_updatesAndReturnsResponse() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setFirstName("Rahul");
        request.setEmail("TEACHER@DAWNRISE.COM");
        User user = user(1L, "teacher01", "teacher@dawnrise.com");
        UserResponse expectedResponse = response(10L, 1L, "teacher01");

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.updateUserProfile(1L, 10L, request);

        assertSame(expectedResponse, actualResponse);
        verify(userRepository, never())
                .existsByOrganizationIdAndEmail(anyLong(), anyString());
        verify(userMapper).updateProfile(request, user);
        verify(userRepository).save(user);
        verify(userMapper).toResponse(user);
    }

    @Test
    void updateUserProfile_whenValidNewEmail_updatesAndReturnsResponse() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setFirstName("Rahul");
        request.setEmail("new@dawnrise.com");
        User user = user(1L, "teacher01", "old@dawnrise.com");
        UserResponse expectedResponse = response(10L, 1L, "teacher01");

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByOrganizationIdAndEmail(
                1L,
                "new@dawnrise.com"
        )).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.updateUserProfile(1L, 10L, request);

        assertSame(expectedResponse, actualResponse);
        verify(userMapper).updateProfile(request, user);
        verify(userRepository).save(user);
        verify(userMapper).toResponse(user);
    }

    @Test
    void updateUserStatus_whenUpdaterInactive_throwsApprovalNotAllowedException() {
        UpdateUserStatusRequest request =
                new UpdateUserStatusRequest(UserStatus.SUSPENDED);
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setRoles(Set.of(UserRole.ADMIN));
        updater.setStatus(UserStatus.SUSPENDED);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));

        ApprovalNotAllowedException exception = assertThrows(
                ApprovalNotAllowedException.class,
                () -> userService.updateUserStatus(1L, auth(99L), 10L, request)
        );

        assertEquals(
                "Only an active user can update account status",
                exception.getMessage()
        );
        verify(userRepository, never())
                .findByOrganizationIdAndId(1L, 10L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRoles_whenUserMissing_throwsResourceNotFoundException() {
        UpdateUserRolesRequest request =
                new UpdateUserRolesRequest(Set.of(UserRole.ADMIN));
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setRoles(Set.of(UserRole.ADMIN));
        updater.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.updateUserRoles(1L, auth(99L), 10L, request)
        );

        assertEquals(
                "User not found with ID: 10 in organization: 1",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRoles_whenUpdaterInactive_throwsInvalidRoleRequestException() {
        UpdateUserRolesRequest request =
                new UpdateUserRolesRequest(Set.of(UserRole.TEACHER));
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setStatus(UserStatus.SUSPENDED);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));

        InvalidRoleRequestException exception = assertThrows(
                InvalidRoleRequestException.class,
                () -> userService.updateUserRoles(1L, auth(99L), 10L, request)
        );

        assertEquals(
                "Only an active user can update roles",
                exception.getMessage()
        );
        verify(userRepository, never())
                .findByOrganizationIdAndId(1L, 10L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRoles_whenUpdatingOwnRoles_throwsInvalidRoleRequestException() {
        UpdateUserRolesRequest request =
                new UpdateUserRolesRequest(Set.of(UserRole.TEACHER));
        User updater = user(10L, 1L, "admin01", "admin@dawnrise.com");
        updater.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(updater));

        InvalidRoleRequestException exception = assertThrows(
                InvalidRoleRequestException.class,
                () -> userService.updateUserRoles(1L, auth(10L), 10L, request)
        );

        assertEquals(
                "You cannot update your own roles",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRoles_whenTargetInactive_throwsInvalidRoleRequestException() {
        UpdateUserRolesRequest request =
                new UpdateUserRolesRequest(Set.of(UserRole.TEACHER));
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setStatus(UserStatus.ACTIVE);
        User target = user(10L, 1L, "teacher01", "teacher@dawnrise.com");
        target.setStatus(UserStatus.PENDING_APPROVAL);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(target));

        InvalidRoleRequestException exception = assertThrows(
                InvalidRoleRequestException.class,
                () -> userService.updateUserRoles(1L, auth(99L), 10L, request)
        );

        assertEquals(
                "Roles can only be updated for an active user",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRoles_whenOnlyExistingRolesRequested_returnsCurrentUser() {
        UpdateUserRolesRequest request =
                new UpdateUserRolesRequest(Set.of(UserRole.TEACHER));
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setStatus(UserStatus.ACTIVE);
        User target = user(10L, 1L, "teacher01", "teacher@dawnrise.com");
        target.setRoles(Set.of(UserRole.TEACHER));
        target.setStatus(UserStatus.ACTIVE);
        UserResponse expectedResponse = response(10L, 1L, "teacher01");

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(target));
        when(userMapper.toResponse(target)).thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.updateUserRoles(1L, auth(99L), 10L, request);

        assertSame(expectedResponse, actualResponse);
        verify(userRepository, never()).save(any(User.class));
        verify(roleRequestRepository, never()).save(any());
    }

    @Test
    void updateUserRoles_whenSensitiveRoleNotAllowed_throwsInvalidRoleRequestException() {
        UpdateUserRolesRequest request =
                new UpdateUserRolesRequest(Set.of(UserRole.ADMIN));
        User updater = user(99L, 1L, "teacher02", "teacher2@dawnrise.com");
        updater.setRoles(Set.of(UserRole.TEACHER));
        updater.setStatus(UserStatus.ACTIVE);
        User target = user(10L, 1L, "teacher01", "teacher@dawnrise.com");
        target.setRoles(Set.of(UserRole.TEACHER));
        target.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(target));
        when(roleApprovalPolicy.getRoutineRoles(Set.of(UserRole.ADMIN)))
                .thenReturn(Set.of());
        when(roleApprovalPolicy.getSensitiveRoles(Set.of(UserRole.ADMIN)))
                .thenReturn(Set.of(UserRole.ADMIN));
        when(roleApprovalPolicy.canRequestApproval(
                Set.of(UserRole.TEACHER),
                UserRole.ADMIN
        )).thenReturn(false);

        InvalidRoleRequestException exception = assertThrows(
                InvalidRoleRequestException.class,
                () -> userService.updateUserRoles(1L, auth(99L), 10L, request)
        );

        assertEquals(
                "You are not allowed to request role: ADMIN",
                exception.getMessage()
        );
        verify(roleRequestRepository, never())
                .existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                        anyLong(),
                        anyLong(),
                        any(),
                        any()
                );
        verify(userRepository, never()).save(any(User.class));
        verify(roleRequestRepository, never()).save(any());
    }

    @Test
    void updateUserRoles_whenSensitiveRoleAlreadyPending_throwsDuplicateResourceException() {
        UpdateUserRolesRequest request =
                new UpdateUserRolesRequest(Set.of(UserRole.ADMIN));
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setRoles(Set.of(UserRole.ADMIN));
        updater.setStatus(UserStatus.ACTIVE);
        User target = user(10L, 1L, "teacher01", "teacher@dawnrise.com");
        target.setRoles(Set.of(UserRole.TEACHER));
        target.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(target));
        when(roleApprovalPolicy.getRoutineRoles(Set.of(UserRole.ADMIN)))
                .thenReturn(Set.of());
        when(roleApprovalPolicy.getSensitiveRoles(Set.of(UserRole.ADMIN)))
                .thenReturn(Set.of(UserRole.ADMIN));
        when(roleApprovalPolicy.canRequestApproval(
                Set.of(UserRole.ADMIN),
                UserRole.ADMIN
        )).thenReturn(true);
        when(roleRequestRepository
                .existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                        1L,
                        10L,
                        UserRole.ADMIN,
                        ApprovalStatus.PENDING
                )).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.updateUserRoles(1L, auth(99L), 10L, request)
        );

        assertEquals(
                "A pending request already exists for user 10 and role ADMIN",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
        verify(roleRequestRepository, never()).save(any());
    }

    @Test
    void updateUserRoles_whenValid_updatesRolesAndReturnsResponse() {
        UpdateUserRolesRequest request =
                new UpdateUserRolesRequest(Set.of(UserRole.ADMIN,
                        UserRole.TEACHER));
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setRoles(Set.of(UserRole.ADMIN));
        updater.setStatus(UserStatus.ACTIVE);
        User user = user(10L, 1L, "teacher01", "teacher@dawnrise.com");
        user.setRoles(Set.of());
        user.setStatus(UserStatus.ACTIVE);
        UserResponse expectedResponse = response(10L, 1L, "teacher01");
        RoleAssignmentRequest approvalRequest =
                new RoleAssignmentRequest(
                        1L,
                        10L,
                        UserRole.ADMIN,
                        99L,
                        "Role change requested by an authorized user"
                );

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user));
        when(roleApprovalPolicy.getRoutineRoles(Set.of(
                UserRole.ADMIN,
                UserRole.TEACHER
        ))).thenReturn(Set.of(UserRole.TEACHER));
        when(roleApprovalPolicy.getSensitiveRoles(Set.of(
                UserRole.ADMIN,
                UserRole.TEACHER
        ))).thenReturn(Set.of(UserRole.ADMIN));
        when(roleApprovalPolicy.canRequestApproval(
                Set.of(UserRole.ADMIN),
                UserRole.ADMIN
        )).thenReturn(true);
        when(roleRequestRepository
                .existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                        1L,
                        10L,
                        UserRole.ADMIN,
                        ApprovalStatus.PENDING
                )).thenReturn(false);
        when(roleRequestMapper.toEntity(
                eq(1L),
                eq(99L),
                any()
        )).thenReturn(approvalRequest);
        when(roleRequestRepository.save(approvalRequest))
                .thenReturn(approvalRequest);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.updateUserRoles(1L, auth(99L), 10L, request);

        assertSame(expectedResponse, actualResponse);
        assertEquals(Set.of(UserRole.TEACHER), user.getRoles());
        verify(roleRequestRepository).save(approvalRequest);
        verify(roleAssignmentWorkflowService).applyRequesterSignOff(
                1L,
                approvalRequest,
                updater
        );
        verify(userRepository).save(user);
        verify(userMapper).toResponse(user);
    }

    @Test
    void updateUserStatus_whenUserMissing_throwsResourceNotFoundException() {
        UpdateUserStatusRequest request =
                new UpdateUserStatusRequest(UserStatus.ACTIVE);
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setRoles(Set.of(UserRole.ADMIN));
        updater.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.updateUserStatus(1L, auth(99L), 10L, request)
        );

        assertEquals(
                "User not found with ID: 10 in organization: 1",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserStatus_whenTransitionInvalid_throwsInvalidUserStatusTransitionException() {
        UpdateUserStatusRequest request =
                new UpdateUserStatusRequest(UserStatus.ACTIVE);
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setRoles(Set.of(UserRole.ADMIN));
        updater.setStatus(UserStatus.ACTIVE);
        User target = user(10L, 1L, "teacher01", "teacher@dawnrise.com");
        target.setStatus(UserStatus.PENDING_ACTIVATION);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(target));
        when(statusTransitionPolicy.canTransitionAdministratively(
                UserStatus.PENDING_ACTIVATION,
                UserStatus.ACTIVE
        )).thenReturn(false);

        InvalidUserStatusTransitionException exception = assertThrows(
                InvalidUserStatusTransitionException.class,
                () -> userService.updateUserStatus(1L, auth(99L), 10L, request)
        );

        assertEquals(
                "Status transition from PENDING_ACTIVATION to ACTIVE "
                        + "is not allowed",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void updateUserStatus_whenStatusUnchanged_returnsCurrentUser() {
        UpdateUserStatusRequest request =
                new UpdateUserStatusRequest(UserStatus.ACTIVE);
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setRoles(Set.of(UserRole.ADMIN));
        updater.setStatus(UserStatus.ACTIVE);
        User target = user(10L, 1L, "teacher01", "teacher@dawnrise.com");
        target.setStatus(UserStatus.ACTIVE);
        UserResponse expectedResponse = response(10L, 1L, "teacher01");

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(target));
        when(statusTransitionPolicy.canTransitionAdministratively(
                UserStatus.ACTIVE,
                UserStatus.ACTIVE
        )).thenReturn(true);
        when(userMapper.toResponse(target)).thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.updateUserStatus(1L, auth(99L), 10L, request);

        assertSame(expectedResponse, actualResponse);
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void updateUserStatus_whenSuspendingUser_revokesRefreshTokens() {
        UpdateUserStatusRequest request =
                new UpdateUserStatusRequest(UserStatus.SUSPENDED);
        User updater = user(99L, 1L, "admin01", "admin@dawnrise.com");
        updater.setRoles(Set.of(UserRole.ADMIN));
        updater.setStatus(UserStatus.ACTIVE);
        User target = user(10L, 1L, "teacher01", "teacher@dawnrise.com");
        target.setStatus(UserStatus.ACTIVE);
        UserResponse expectedResponse = response(10L, 1L, "teacher01");

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(updater));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(target));
        when(statusTransitionPolicy.canTransitionAdministratively(
                UserStatus.ACTIVE,
                UserStatus.SUSPENDED
        )).thenReturn(true);
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(expectedResponse);

        UserResponse actualResponse =
                userService.updateUserStatus(1L, auth(99L), 10L, request);

        assertSame(expectedResponse, actualResponse);
        assertEquals(UserStatus.SUSPENDED, target.getStatus());
        verify(userRepository).save(target);
        verify(refreshTokenService).revokeAllForUser(10L);
        verify(userMapper).toResponse(target);
    }

    private static User user(
            Long id,
            Long organizationId,
            String username,
            String email
    ) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setOrganizationId(organizationId);
        user.setUsername(username);
        user.setFirstName("Rahul");
        user.setEmail(email);
        user.setRoles(Set.of(UserRole.TEACHER));
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        return user;
    }

    private static User user(
            Long organizationId,
            String username,
            String email
    ) {
        return user(10L, organizationId, username, email);
    }

    private static UserResponse response(
            Long id,
            Long organizationId,
            String username
    ) {
        UserResponse response = new UserResponse();
        response.setId(id);
        response.setOrganizationId(organizationId);
        response.setUsername(username);
        return response;
    }

    private static AuthorizationContext auth(Long userId) {
        return new AuthorizationContext(
                userId,
                Set.of(
                        PermissionCode.USER_CREATE,
                        PermissionCode.ROLE_ASSIGN_ROUTINE,
                        PermissionCode.ROLE_REMOVE_ROUTINE,
                        PermissionCode.ROLE_ASSIGNMENT_REQUEST_CREATE,
                        PermissionCode.USER_SUSPEND,
                        PermissionCode.USER_DEACTIVATE,
                        PermissionCode.USER_REACTIVATE,
                        PermissionCode.USER_ACTIVATION_RESEND
                )
        );
    }
}
