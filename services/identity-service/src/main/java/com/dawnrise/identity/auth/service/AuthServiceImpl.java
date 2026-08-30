package com.dawnrise.identity.auth.service;

import com.dawnrise.identity.auth.config.PasswordChangeProperties;
import com.dawnrise.identity.auth.dto.ChangePasswordRequest;
import com.dawnrise.identity.auth.dto.LoginRequest;
import com.dawnrise.identity.auth.dto.LoginResponse;
import com.dawnrise.identity.auth.activation.exception.PasswordMismatchException;
import com.dawnrise.identity.auth.exception.AccountNotActiveException;
import com.dawnrise.identity.auth.exception.InvalidCredentialsException;
import com.dawnrise.identity.auth.exception.PasswordChangeNotAllowedException;
import com.dawnrise.identity.auth.lockout.LoginLockoutService;
import com.dawnrise.identity.auth.model.AuthenticationResult;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenRotationResult;
import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.auth.security.JwtService;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.organization.entity.Organization;
import com.dawnrise.identity.organization.repository.OrganizationRepository;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.permission.service.PermissionService;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Invalid username or password";
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginLockoutService loginLockoutService;
    private final PasswordChangeProperties passwordChangeProperties;
    private final PermissionService permissionService;
    private final SecurityAuditService auditService;

    public AuthServiceImpl(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginLockoutService loginLockoutService,
            PasswordChangeProperties passwordChangeProperties,
            PermissionService permissionService,
            SecurityAuditService auditService
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginLockoutService = loginLockoutService;
        this.passwordChangeProperties = passwordChangeProperties;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public AuthenticationResult login(
            LoginRequest request
    ) {
        // Use one generic error so login cannot reveal valid usernames.
        Organization organization = organizationRepository
                .findBySchoolCodeIgnoreCase(request.getSchoolCode().trim())
                .orElse(null);

        Long organizationId = organization == null
                ? null
                : organization.getId();
        String usernameOrEmail = request.getUsernameOrEmail().trim();
        User user = organizationId == null
                ? null
                : (usernameOrEmail.contains("@")
                    ? userRepository.findByOrganizationIdAndEmailIgnoreCase(
                            organizationId,
                            usernameOrEmail
                    ).orElse(null)
                    : userRepository.findByOrganizationIdAndUsername(
                            organizationId,
                            usernameOrEmail
                    ).orElse(null));

        if (user == null) {
            if (organizationId != null) {
                auditService.record(
                        organizationId,
                        null,
                        SecurityAuditAction.LOGIN_FAILURE,
                        SecurityAuditOutcome.FAILURE,
                        "USER",
                        null,
                        Map.of("reason", "invalid_credentials")
                );
            }
            throw new InvalidCredentialsException(
                    INVALID_CREDENTIALS_MESSAGE
            );
        }

        OffsetDateTime currentTime = OffsetDateTime.now();

        // Lockout is checked before password work to block locked accounts fast.
        loginLockoutService.checkLoginAllowed(user, currentTime);

        boolean passwordMatches =
                user.getPasswordHash() != null
                        && passwordEncoder.matches(
                        request.getPassword(),
                        user.getPasswordHash()
                );

        if (!passwordMatches) {
            auditService.record(
                    user.getOrganizationId(),
                    user.getId(),
                    SecurityAuditAction.LOGIN_FAILURE,
                    SecurityAuditOutcome.FAILURE,
                    "USER",
                    user.getId(),
                    Map.of("reason", "invalid_credentials")
            );

            // Failed attempts are counted even though the public error is generic.
            loginLockoutService.recordFailedLogin(
                    user,
                    currentTime
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Account is not active"
            );
        }

        loginLockoutService.recordSuccessfulLogin(user);
        user.setLastLoginAt(currentTime);

        // Refresh tokens are persisted separately and returned only as cookies.
        String rawRefreshToken =
                refreshTokenService.createRefreshToken(
                        user.getId()
                );

        auditService.record(
                user.getOrganizationId(),
                user.getId(),
                SecurityAuditAction.LOGIN_SUCCESS,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                user.getId(),
                Map.of("username", user.getUsername())
        );

        return createAuthenticationResult(
                user,
                rawRefreshToken
        );
    }

    @Override
    @Transactional
    public AuthenticationResult refresh(
            String rawRefreshToken
    ) {
        // Rotation invalidates the submitted refresh token on every use.
        RefreshTokenRotationResult rotationResult =
                refreshTokenService.rotateRefreshToken(
                        rawRefreshToken
                );

        User user = userRepository
                .findById(rotationResult.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            // Inactive users lose all sessions, including other devices.
            refreshTokenService.revokeAllForUser(
                    user.getId()
            );

            throw new AccountNotActiveException(
                    "Account is not active"
            );
        }

        return createAuthenticationResult(
                user,
                rotationResult.rawRefreshToken()
        );
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeRefreshToken(
                rawRefreshToken
        );
    }

    @Override
    @Transactional
    public void changePassword(
            Long userId,
            ChangePasswordRequest request
    ) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            // Confirmation is checked here because it compares two fields.
            throw new PasswordMismatchException(
                    "Password and confirmation do not match"
            );
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Account is not active"
            );
        }

        boolean currentPasswordMatches =
                user.getPasswordHash() != null
                        && passwordEncoder.matches(
                        request.getCurrentPassword(),
                        user.getPasswordHash()
                );

        if (!currentPasswordMatches) {
            // Authenticated password changes still require password proof.
            throw new InvalidCredentialsException(
                    "Current password is incorrect"
            );
        }

        enforcePasswordChangeCooldown(user);

        user.resetPassword(
                passwordEncoder.encode(request.getNewPassword())
        );

        // Password changes invalidate stolen or unattended sessions.
        refreshTokenService.revokeAllForUser(user.getId());

        auditService.record(
                user.getOrganizationId(),
                user.getId(),
                SecurityAuditAction.PASSWORD_CHANGE,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                user.getId(),
                Map.of("sessionsRevoked", true)
        );
    }

    private void enforcePasswordChangeCooldown(User user) {
        // Cooldown applies only to ordinary logged-in password changes.
        if (user.getPasswordChangedAt() == null
                || passwordChangeProperties.getCooldown() == null
                || passwordChangeProperties.getCooldown().isZero()
                || passwordChangeProperties.getCooldown().isNegative()) {
            return;
        }

        OffsetDateTime allowedAt =
                user.getPasswordChangedAt()
                        .plus(passwordChangeProperties.getCooldown());

        if (allowedAt.isAfter(OffsetDateTime.now())) {
            throw new PasswordChangeNotAllowedException(
                    "Password can be changed again after " + allowedAt
            );
        }
    }

    private AuthenticationResult createAuthenticationResult(
            User user,
            String rawRefreshToken
    ) {
        Set<PermissionCode> permissions =
                permissionService.getActivePermissionsForRoles(
                        user.getRoles()
                );

        String accessToken =
                jwtService.generateAccessToken(
                        user,
                        permissions
                );

        Set<String> roles = user.getRoles()
                .stream()
                .map(UserRole::name)
                .collect(Collectors.toSet());

        String organizationName = organizationRepository
                .findById(user.getOrganizationId())
                .map(Organization::getName)
                .orElse(null);

        LoginResponse response = new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                user.getId(),
                user.getOrganizationId(),
                organizationName,
                user.getUsername(),
                roles
        );

        return new AuthenticationResult(
                response,
                rawRefreshToken
        );
    }

}
