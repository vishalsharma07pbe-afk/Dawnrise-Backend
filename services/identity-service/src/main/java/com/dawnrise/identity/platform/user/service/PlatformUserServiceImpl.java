package com.dawnrise.identity.platform.user.service;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.platform.auth.security.PlatformAuthorizationContext;
import com.dawnrise.identity.platform.audit.service.PlatformSecurityAuditService;
import com.dawnrise.identity.platform.permission.enums.PlatformPermissionCode;
import com.dawnrise.identity.platform.user.dto.CreatePlatformUserRequest;
import com.dawnrise.identity.platform.user.dto.PlatformUserResponse;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import com.dawnrise.identity.platform.user.mapper.PlatformUserMapper;
import com.dawnrise.identity.platform.user.policy.PlatformRoleAssignmentPolicy;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
import com.dawnrise.identity.platform.auth.activation.event.PlatformUserActivationRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;

import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class PlatformUserServiceImpl
        implements PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformUserMapper platformUserMapper;
    private final PlatformRoleAssignmentPolicy roleAssignmentPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformSecurityAuditService auditService;

    public PlatformUserServiceImpl(
            PlatformUserRepository platformUserRepository,
            PlatformUserMapper platformUserMapper,
            PlatformRoleAssignmentPolicy roleAssignmentPolicy,
            ApplicationEventPublisher eventPublisher,
            PlatformSecurityAuditService auditService
    ) {
        this.platformUserRepository = platformUserRepository;
        this.platformUserMapper = platformUserMapper;
        this.roleAssignmentPolicy = roleAssignmentPolicy;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    @Override
    public PlatformUserResponse createPlatformUser(
            PlatformAuthorizationContext authorizationContext,
            CreatePlatformUserRequest request
    ) {
        requireActiveActor(authorizationContext);

        requirePermission(
                authorizationContext,
                PlatformPermissionCode.PLATFORM_USER_CREATE
        );

        requirePermission(
                authorizationContext,
                PlatformPermissionCode.PLATFORM_ROLE_ASSIGN
        );

        roleAssignmentPolicy.validateRoleAssignment(
                authorizationContext,
                request.getRoles()
        );

        String normalizedUsername =
                normalizeRequiredIdentityValue(
                        request.getUsername()
                );

        String normalizedEmail =
                normalizeRequiredIdentityValue(
                        request.getEmail()
                );

        if (platformUserRepository.existsByUsernameIgnoreCase(
                normalizedUsername
        )) {
            throw new DuplicateResourceException(
                    "Platform username already exists: "
                            + normalizedUsername
            );
        }

        if (platformUserRepository.existsByEmailIgnoreCase(
                normalizedEmail
        )) {
            throw new DuplicateResourceException(
                    "Platform email already exists: "
                            + normalizedEmail
            );
        }

        PlatformUser platformUser = new PlatformUser(
                normalizedUsername,
                normalizeRequiredText(request.getFirstName()),
                normalizedEmail,
                request.getRoles()
        );

        platformUser.setMiddleName(
                normalizeOptionalText(request.getMiddleName())
        );

        platformUser.setLastName(
                normalizeOptionalText(request.getLastName())
        );

        platformUser.setPhone(
                normalizeOptionalText(request.getPhone())
        );

        /*
         * Password hash remains null.
         * The employee will create their password using a
         * single-use activation link.
         */
        platformUser.setStatus(
                PlatformUserStatus.PENDING_ACTIVATION
        );

        PlatformUser savedPlatformUser =
                platformUserRepository.save(platformUser);

        eventPublisher.publishEvent(
                new PlatformUserActivationRequestedEvent(
                        savedPlatformUser.getId()
                )
        );

        auditService.record(
                authorizationContext.getPlatformUserId(),
                SecurityAuditAction.PLATFORM_USER_CREATE,
                SecurityAuditOutcome.SUCCESS,
                "PLATFORM_USER",
                savedPlatformUser.getId(),
                Map.of("roles", savedPlatformUser.getRoles())
        );

        return platformUserMapper.toResponse(savedPlatformUser);
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformUserResponse getPlatformUserById(
            PlatformAuthorizationContext authorizationContext,
            Long platformUserId
    ) {
        requireActiveActor(authorizationContext);

        requirePermission(
                authorizationContext,
                PlatformPermissionCode.PLATFORM_USER_VIEW
        );

        PlatformUser platformUser = platformUserRepository
                .findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform user not found with ID: "
                                + platformUserId
                ));

        return platformUserMapper.toResponse(platformUser);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlatformUserResponse> getPlatformUsers(
            PlatformAuthorizationContext authorizationContext,
            PlatformUserStatus status,
            String search,
            Pageable pageable
    ) {
        requireActiveActor(authorizationContext);

        requirePermission(
                authorizationContext,
                PlatformPermissionCode.PLATFORM_USER_VIEW
        );

        String normalizedSearch =
                normalizeOptionalText(search);

        Page<PlatformUserResponse> responsePage =
                platformUserRepository
                        .searchPlatformUsers(
                                status,
                                normalizedSearch,
                                pageable
                        )
                        .map(platformUserMapper::toResponse);

        return PageResponse.from(responsePage);
    }

    @Override
    public void resendActivationLink(
            PlatformAuthorizationContext authorizationContext,
            Long platformUserId
    ) {
        requireActiveActor(authorizationContext);

        requirePermission(
                authorizationContext,
                PlatformPermissionCode.PLATFORM_USER_ACTIVATE
        );

        PlatformUser platformUser = platformUserRepository
                .findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform user not found with ID: "
                                + platformUserId
                ));

        if (platformUser.getStatus()
                != PlatformUserStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException(
                    "Only pending platform users can be resent activation"
            );
        }

        eventPublisher.publishEvent(
                new PlatformUserActivationRequestedEvent(
                        platformUser.getId()
                )
        );

        auditService.record(
                authorizationContext.getPlatformUserId(),
                SecurityAuditAction.PLATFORM_ACTIVATION_RESEND,
                SecurityAuditOutcome.SUCCESS,
                "PLATFORM_USER",
                platformUser.getId(),
                Map.of("requestedBy", "platform_admin")
        );
    }

    private PlatformUser requireActiveActor(
            PlatformAuthorizationContext authorizationContext
    ) {
        if (authorizationContext == null) {
            throw new AccessDeniedException(
                    "Platform authorization is required"
            );
        }

        PlatformUser actor = platformUserRepository
                .findById(
                        authorizationContext.getPlatformUserId()
                )
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated platform user was not found"
                ));

        if (actor.getStatus() != PlatformUserStatus.ACTIVE) {
            throw new AccessDeniedException(
                    "Only an active platform user can perform this operation"
            );
        }

        return actor;
    }

    private void requirePermission(
            PlatformAuthorizationContext authorizationContext,
            PlatformPermissionCode requiredPermission
    ) {
        if (!authorizationContext.hasPermission(
                requiredPermission
        )) {
            throw new AccessDeniedException(
                    "Missing platform permission: "
                            + requiredPermission
            );
        }
    }

    private String normalizeRequiredIdentityValue(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required identity value cannot be blank"
            );
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required text value cannot be blank"
            );
        }

        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
