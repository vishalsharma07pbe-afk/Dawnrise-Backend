package com.dawnrise.identity.user.service;

import com.dawnrise.identity.auth.activation.event.UserActivationRequestedEvent;
import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.roleapproval.dto.CreateRoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleapproval.exception.InvalidRoleRequestException;
import com.dawnrise.identity.roleapproval.mapper.RoleAssignmentRequestMapper;
import com.dawnrise.identity.roleapproval.policy.RoleApprovalPolicy;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentRequestRepository;
import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.roleremoval.repository.RoleRemovalRequestRepository;
import com.dawnrise.identity.user.exception.InvalidUserStatusTransitionException;
import com.dawnrise.identity.user.policy.UserStatusAuthorizationPolicy;
import com.dawnrise.identity.user.policy.UserStatusTransitionPolicy;
import com.dawnrise.identity.user.dto.CreateUserRequest;
import com.dawnrise.identity.user.dto.UpdateUserProfileRequest;
import com.dawnrise.identity.user.dto.UpdateUserRolesRequest;
import com.dawnrise.identity.user.dto.UpdateUserStatusRequest;
import com.dawnrise.identity.user.dto.UserResponse;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.mapper.UserMapper;
import com.dawnrise.identity.user.repository.UserRepository;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final RoleApprovalPolicy roleApprovalPolicy;
    private final RoleAssignmentRequestRepository roleRequestRepository;
    private final RoleRemovalRequestRepository roleRemovalRequestRepository;
    private final RoleAssignmentRequestMapper roleRequestMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UserStatusTransitionPolicy statusTransitionPolicy;
    private final UserStatusAuthorizationPolicy statusAuthorizationPolicy;
    private final RefreshTokenService refreshTokenService;
    private final SecurityAuditService auditService;

    public UserServiceImpl(
            UserRepository userRepository,
            UserMapper userMapper,
            RoleApprovalPolicy roleApprovalPolicy,
            RoleAssignmentRequestRepository roleRequestRepository,
            RoleRemovalRequestRepository roleRemovalRequestRepository,
            RoleAssignmentRequestMapper roleRequestMapper,
            ApplicationEventPublisher eventPublisher,
            UserStatusTransitionPolicy statusTransitionPolicy,
            UserStatusAuthorizationPolicy statusAuthorizationPolicy,
            RefreshTokenService refreshTokenService,
            SecurityAuditService auditService
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.roleApprovalPolicy = roleApprovalPolicy;
        this.roleRequestRepository = roleRequestRepository;
        this.roleRemovalRequestRepository = roleRemovalRequestRepository;
        this.roleRequestMapper = roleRequestMapper;
        this.eventPublisher = eventPublisher;
        this.statusTransitionPolicy = statusTransitionPolicy;
        this.statusAuthorizationPolicy = statusAuthorizationPolicy;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public UserResponse createUser(
            Long organizationId,
            AuthorizationContext authorizationContext,
            CreateUserRequest request
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.USER_CREATE
        );

        // Load the authenticated creator from the secured organization.
        User creator = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        authorizationContext.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Creating user not found"
                ));

        // Only active users can create another account.
        if (creator.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRoleRequestException(
                    "Only an active user can create another user"
            );
        }

        // Check username uniqueness within the organization.
        if (userRepository.existsByOrganizationIdAndUsername(
                organizationId,
                request.getUsername()
        )) {
            throw new DuplicateResourceException(
                    "Username already exists in this organization: "
                            + request.getUsername()
            );
        }

        // Check email uniqueness within the organization.
        if (request.getEmail() != null
                && !request.getEmail().isBlank()
                && userRepository.existsByOrganizationIdAndEmail(
                organizationId,
                request.getEmail()
        )) {
            throw new DuplicateResourceException(
                    "Email already exists in this organization: "
                            + request.getEmail()
            );
        }

        // Separate immediately assignable roles from approval-protected roles.
        Set<UserRole> routineRoles =
                roleApprovalPolicy.getRoutineRoles(request.getRoles());

        Set<UserRole> sensitiveRoles =
                roleApprovalPolicy.getSensitiveRoles(request.getRoles());

        if (!sensitiveRoles.isEmpty()) {
            requirePermission(
                    authorizationContext,
                    PermissionCode.ROLE_ASSIGNMENT_REQUEST_CREATE
            );
        }

        // Validate permission to request every sensitive role.
        for (UserRole sensitiveRole : sensitiveRoles) {
            if (!roleApprovalPolicy.canRequestApproval(
                    creator.getRoles(),
                    sensitiveRole
            )) {
                throw new InvalidRoleRequestException(
                        "You are not allowed to request role: "
                                + sensitiveRole
                );
            }
        }

        // Map the administrative request without creating a password.
        User user = userMapper.toEntity(request);

        // Assign only routine roles and trust the organization ID from the URL.
        user.setRoles(routineRoles);
        user.setOrganizationId(organizationId);

        // Sensitive-role accounts wait for approval before activation.
        if (sensitiveRoles.isEmpty()) {
            user.setStatus(UserStatus.PENDING_ACTIVATION);
        } else {
            user.setStatus(UserStatus.PENDING_APPROVAL);
        }

        User savedUser = userRepository.save(user);

        // Create one independent approval request for every sensitive role.
        for (UserRole sensitiveRole : sensitiveRoles) {
            CreateRoleAssignmentRequest approvalRequest =
                    new CreateRoleAssignmentRequest(
                            savedUser.getId(),
                            sensitiveRole,
                            "Role requested during account creation"
                    );

            RoleAssignmentRequest roleRequest =
                    roleRequestMapper.toEntity(
                            organizationId,
                            authorizationContext.getUserId(),
                            approvalRequest
                    );

            roleRequestRepository.save(roleRequest);
        }

        // Send activation only after a routine-role user is committed successfully.
        if (savedUser.getStatus() == UserStatus.PENDING_ACTIVATION) {
            eventPublisher.publishEvent(
                    new UserActivationRequestedEvent(
                            savedUser.getId()
                    )
            );
        }

        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.USER_CREATE,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                savedUser.getId(),
                Map.of("status", savedUser.getStatus())
        );

        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(
            Long organizationId,
            Long userId
    ) {
        User user = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        userId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId
                                + " in organization: " + organizationId
                ));

        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsersByOrganization(
            Long organizationId,
            Pageable pageable
    ) {
        Page<UserResponse> userResponsePage = userRepository
                .findAllByOrganizationId(
                        organizationId,
                        pageable
                )
                .map(userMapper::toResponse);

        return PageResponse.from(userResponsePage);
    }

    @Override
    public UserResponse updateUserProfile(
            Long organizationId,
            Long userId,
            UpdateUserProfileRequest request
    ) {
        User user = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        userId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId
                                + " in organization: " + organizationId
                ));

        // Prevent duplicate email addresses within the organization.
        if (request.getEmail() != null
                && !request.getEmail().isBlank()
                && !request.getEmail().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByOrganizationIdAndEmail(
                organizationId,
                request.getEmail()
        )) {
            throw new DuplicateResourceException(
                    "Email already exists in this organization: "
                            + request.getEmail()
            );
        }

        userMapper.updateProfile(request, user);

        User updatedUser = userRepository.save(user);

        return userMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse updateUserRoles(
            Long organizationId,
            AuthorizationContext authorizationContext,
            Long userId,
            UpdateUserRolesRequest request
    ) {
        User updater = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        authorizationContext.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role-updating user not found"
                ));

        // Only active users can update another user's roles.
        if (updater.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRoleRequestException(
                    "Only an active user can update roles"
            );
        }

        User targetUser = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        userId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId
                                + " in organization: " + organizationId
                ));

        // Prevent users from assigning roles to themselves.
        if (updater.getId().equals(targetUser.getId())) {
            throw new InvalidRoleRequestException(
                    "You cannot update your own roles"
            );
        }

        // Only active existing accounts can receive role updates.
        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRoleRequestException(
                    "Roles can only be updated for an active user"
            );
        }

        Set<UserRole> requestedRoles =
                new HashSet<>(request.getRoles());

        Set<UserRole> rolesToAdd =
                new HashSet<>(requestedRoles);

        rolesToAdd.removeAll(targetUser.getRoles());

        Set<UserRole> rolesToRemove =
                new HashSet<>(targetUser.getRoles());

        rolesToRemove.removeAll(requestedRoles);

        Set<UserRole> removedSensitiveRoles =
                rolesToRemove.isEmpty()
                        ? Set.of()
                        : roleApprovalPolicy.getSensitiveRoles(rolesToRemove);

        if (!removedSensitiveRoles.isEmpty()) {
            throw new InvalidRoleRequestException(
                    "Sensitive role removal requires a separate approval workflow"
            );
        }

        if (rolesToAdd.isEmpty() && rolesToRemove.isEmpty()) {
            return userMapper.toResponse(targetUser);
        }

        // Separate routine roles from approval-protected roles.
        Set<UserRole> routineRoles =
                rolesToAdd.isEmpty()
                        ? Set.of()
                        : roleApprovalPolicy.getRoutineRoles(rolesToAdd);

        Set<UserRole> sensitiveRoles =
                rolesToAdd.isEmpty()
                        ? Set.of()
                        : roleApprovalPolicy.getSensitiveRoles(rolesToAdd);

        if (!routineRoles.isEmpty()) {
            requirePermission(
                    authorizationContext,
                    PermissionCode.ROLE_ASSIGN_ROUTINE
            );
        }

        if (!rolesToRemove.isEmpty()) {
            requirePermission(
                    authorizationContext,
                    PermissionCode.ROLE_REMOVE_ROUTINE
            );
        }

        if (!sensitiveRoles.isEmpty()) {
            requirePermission(
                    authorizationContext,
                    PermissionCode.ROLE_ASSIGNMENT_REQUEST_CREATE
            );
        }

        // Validate permission and prevent duplicate pending requests.
        for (UserRole sensitiveRole : sensitiveRoles) {
            if (!roleApprovalPolicy.canRequestApproval(
                    updater.getRoles(),
                    sensitiveRole
            )) {
                throw new InvalidRoleRequestException(
                        "You are not allowed to request role: "
                                + sensitiveRole
                );
            }

            boolean pendingRequestExists =
                    roleRequestRepository
                            .existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                                    organizationId,
                                    targetUser.getId(),
                                    sensitiveRole,
                                    ApprovalStatus.PENDING
                            );

            if (pendingRequestExists) {
                throw new DuplicateResourceException(
                        "A pending request already exists for user "
                                + targetUser.getId()
                                + " and role "
                                + sensitiveRole
                );
            }
        }

        for (UserRole removedRole : rolesToRemove) {
            boolean pendingRemovalRequestExists =
                    roleRemovalRequestRepository
                            .existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
                                    organizationId,
                                    targetUser.getId(),
                                    removedRole,
                                    ApprovalStatus.PENDING
                            );

            if (pendingRemovalRequestExists) {
                throw new DuplicateResourceException(
                        "A pending removal request already exists for user "
                                + targetUser.getId()
                                + " and role "
                                + removedRole
                );
            }
        }

        // Assign routine roles immediately.
        for (UserRole routineRole : routineRoles) {
            targetUser.addRole(routineRole);
        }

        // Convert every sensitive role into an approval request.
        for (UserRole sensitiveRole : sensitiveRoles) {
            CreateRoleAssignmentRequest approvalRequest =
                    new CreateRoleAssignmentRequest(
                            targetUser.getId(),
                            sensitiveRole,
                            "Role change requested by an authorized user"
                    );

            RoleAssignmentRequest roleRequest =
                    roleRequestMapper.toEntity(
                            organizationId,
                            authorizationContext.getUserId(),
                            approvalRequest
                    );

            roleRequestRepository.save(roleRequest);
        }

        for (UserRole removedRole : rolesToRemove) {
            targetUser.removeRole(removedRole);
        }

        User updatedUser = userRepository.save(targetUser);

        for (UserRole routineRole : routineRoles) {
            auditService.record(
                    organizationId,
                    authorizationContext.getUserId(),
                    SecurityAuditAction.ROUTINE_ROLE_ASSIGN,
                    SecurityAuditOutcome.SUCCESS,
                    "USER",
                    targetUser.getId(),
                    Map.of("role", routineRole)
            );
        }

        for (UserRole removedRole : rolesToRemove) {
            auditService.record(
                    organizationId,
                    authorizationContext.getUserId(),
                    SecurityAuditAction.ROUTINE_ROLE_REMOVE,
                    SecurityAuditOutcome.SUCCESS,
                    "USER",
                    targetUser.getId(),
                    Map.of("role", removedRole)
            );
        }

        return userMapper.toResponse(updatedUser);
    }

    @Override
    public UserResponse updateUserStatus(
            Long organizationId,
            AuthorizationContext authorizationContext,
            Long userId,
            UpdateUserStatusRequest request
    ) {
        // Status changes are restricted because they can disable accounts.
        User updater = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        authorizationContext.getUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Status-updating user not found"
                ));

        if (updater.getStatus() != UserStatus.ACTIVE) {
            throw new ApprovalNotAllowedException(
                    "Only an active user can update account status"
            );
        }

        if (updater.getId().equals(userId)) {
            // Prevent accidental or malicious self-lockout by administrators.
            throw new ApprovalNotAllowedException(
                    "You cannot update your own account status"
            );
        }

        User targetUser = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        userId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId
                                + " in organization: " + organizationId
                ));

        UserStatus currentStatus =
                targetUser.getStatus();

        UserStatus requestedStatus =
                request.getStatus();

        if (!statusTransitionPolicy
                .canTransitionAdministratively(
                        currentStatus,
                        requestedStatus
                )) {
            // Lifecycle-only states cannot be skipped by the status endpoint.
            throw new InvalidUserStatusTransitionException(
                    "Status transition from "
                            + currentStatus
                            + " to "
                            + requestedStatus
                            + " is not allowed"
            );
        }

        if (!statusAuthorizationPolicy.canUpdateStatus(
                currentStatus,
                requestedStatus,
                authorizationContext::hasPermission
        )) {
            throw new ApprovalNotAllowedException(
                    "You are not authorized to update account status"
            );
        }

        if (currentStatus == requestedStatus) {
            // Idempotent requests return the current state without side effects.
            return userMapper.toResponse(targetUser);
        }

        targetUser.setStatus(requestedStatus);

        User updatedUser =
                userRepository.save(targetUser);

        if (requestedStatus == UserStatus.SUSPENDED
                || requestedStatus == UserStatus.INACTIVE) {
            // Disabled accounts must immediately lose all refresh sessions.
            refreshTokenService.revokeAllForUser(
                    targetUser.getId()
            );
        }

        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.USER_STATUS_CHANGE,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                targetUser.getId(),
                Map.of(
                        "fromStatus", currentStatus,
                        "toStatus", requestedStatus
                )
        );

        return userMapper.toResponse(updatedUser);
    }

    @Override
    public void resendActivationLink(
            Long organizationId,
            Long userId,
            AuthorizationContext authorizationContext
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.USER_ACTIVATION_RESEND
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
            throw new ApprovalNotAllowedException(
                    "Only an active user can resend an activation link"
            );
        }

        User targetUser = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        userId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target user not found"
                ));

        if (targetUser.getStatus()
                != UserStatus.PENDING_ACTIVATION) {
            throw new InvalidRoleRequestException(
                    "Only a user pending activation can receive an activation link"
            );
        }

        if (targetUser.getEmail() == null
                || targetUser.getEmail().isBlank()) {
            throw new InvalidRoleRequestException(
                    "The user does not have an email address"
            );
        }

        eventPublisher.publishEvent(
                new UserActivationRequestedEvent(
                        targetUser.getId()
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
