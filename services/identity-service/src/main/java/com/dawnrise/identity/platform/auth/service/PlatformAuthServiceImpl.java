package com.dawnrise.identity.platform.auth.service;

import com.dawnrise.identity.auth.exception.AccountNotActiveException;
import com.dawnrise.identity.auth.exception.InvalidCredentialsException;
import com.dawnrise.identity.auth.lockout.LoginLockoutService;
import com.dawnrise.identity.auth.security.JwtService;
import com.dawnrise.identity.auth.model.IdentityType;
import com.dawnrise.identity.auth.session.entity.AuthenticatedSession;
import com.dawnrise.identity.auth.session.service.AuthenticatedSessionService;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.platform.audit.service.PlatformSecurityAuditService;
import com.dawnrise.identity.platform.auth.dto.PlatformLoginRequest;
import com.dawnrise.identity.platform.auth.dto.PlatformLoginResponse;
import com.dawnrise.identity.platform.auth.model.PlatformAuthenticationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenRotationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenCreationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.service.PlatformRefreshTokenService;
import com.dawnrise.identity.platform.permission.enums.PlatformPermissionCode;
import com.dawnrise.identity.platform.permission.service.PlatformPermissionService;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformRole;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class PlatformAuthServiceImpl implements PlatformAuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Invalid username or password";

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PlatformRefreshTokenService refreshTokenService;
    private final LoginLockoutService loginLockoutService;
    private final PlatformPermissionService permissionService;
    private final PlatformSecurityAuditService auditService;
    private final AuthenticatedSessionService authenticatedSessionService;

    public PlatformAuthServiceImpl(
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PlatformRefreshTokenService refreshTokenService,
            LoginLockoutService loginLockoutService,
            PlatformPermissionService permissionService,
            PlatformSecurityAuditService auditService,
            AuthenticatedSessionService authenticatedSessionService
    ) {
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginLockoutService = loginLockoutService;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.authenticatedSessionService = authenticatedSessionService;
    }

    @Override
    @Transactional
    public PlatformAuthenticationResult login(
            PlatformLoginRequest request
    ) {
        String normalizedUsername = request.getUsername()
                .trim()
                .toLowerCase(Locale.ROOT);

        PlatformUser platformUser = platformUserRepository
                .findByUsernameIgnoreCase(normalizedUsername)
                .orElse(null);

        if (platformUser == null) {
            auditService.record(
                    null,
                    SecurityAuditAction.PLATFORM_LOGIN_FAILURE,
                    SecurityAuditOutcome.FAILURE,
                    "PLATFORM_USER",
                    null,
                    Map.of("reason", "invalid_credentials")
            );
            throw invalidCredentials();
        }

        OffsetDateTime currentTime = OffsetDateTime.now();
        loginLockoutService.checkLoginAllowed(
                platformUser,
                currentTime
        );

        boolean passwordMatches =
                platformUser.getPasswordHash() != null
                        && passwordEncoder.matches(
                        request.getPassword(),
                        platformUser.getPasswordHash()
                );

        if (!passwordMatches) {
            auditService.record(
                    platformUser.getId(),
                    SecurityAuditAction.PLATFORM_LOGIN_FAILURE,
                    SecurityAuditOutcome.FAILURE,
                    "PLATFORM_USER",
                    platformUser.getId(),
                    Map.of("reason", "invalid_credentials")
            );
            loginLockoutService.recordFailedLogin(
                    platformUser,
                    currentTime
            );
        }

        if (platformUser.getStatus() != PlatformUserStatus.ACTIVE) {
            auditService.record(
                    platformUser.getId(),
                    SecurityAuditAction.PLATFORM_LOGIN_FAILURE,
                    SecurityAuditOutcome.FAILURE,
                    "PLATFORM_USER",
                    platformUser.getId(),
                    Map.of("reason", "not_active")
            );
            throw new AccountNotActiveException(
                    "Account is not active"
            );
        }

        loginLockoutService.recordSuccessfulLogin(platformUser);
        platformUser.setLastLoginAt(currentTime);

        PlatformRefreshTokenCreationResult refreshToken =
                refreshTokenService.createRefreshTokenSession(platformUser.getId());
        AuthenticatedSession session = authenticatedSessionService.createPlatformSession(
                        refreshToken.tokenFamilyId(),
                        platformUser.getId(),
                        currentTime
                );

        auditService.record(
                platformUser.getId(),
                SecurityAuditAction.PLATFORM_LOGIN_SUCCESS,
                SecurityAuditOutcome.SUCCESS,
                "PLATFORM_USER",
                platformUser.getId(),
                Map.of("username", platformUser.getUsername())
        );

        return createAuthenticationResult(
                platformUser,
                refreshToken.rawRefreshToken(),
                refreshToken.tokenFamilyId(),
                session.getAuthenticatedAt()
        );
    }

    @Override
    @Transactional
    public PlatformAuthenticationResult refresh(
            String rawRefreshToken
    ) {
        PlatformRefreshTokenRotationResult rotationResult =
                refreshTokenService.rotateRefreshToken(
                        rawRefreshToken
                );

        PlatformUser platformUser = platformUserRepository
                .findById(rotationResult.platformUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform user not found"
                ));

        if (platformUser.getStatus() != PlatformUserStatus.ACTIVE) {
            refreshTokenService.revokeAllForPlatformUser(
                    platformUser.getId()
            );
            throw new AccountNotActiveException(
                    "Account is not active"
            );
        }

        OffsetDateTime authenticatedAt = authenticatedSessionService.validateAndTouch(
                        rotationResult.tokenFamilyId(),
                        IdentityType.PLATFORM_USER,
                        platformUser.getId()
                ).authenticatedAt();

        auditService.record(
                platformUser.getId(),
                SecurityAuditAction.PLATFORM_REFRESH_TOKEN_ROTATE,
                SecurityAuditOutcome.SUCCESS,
                "PLATFORM_USER",
                platformUser.getId(),
                Map.of("rotated", true)
        );

        return createAuthenticationResult(
                platformUser,
                rotationResult.rawRefreshToken(),
                rotationResult.tokenFamilyId(),
                authenticatedAt
        );
    }

    @Override
    @Transactional
    public PlatformAuthenticationResult unlockSession(String rawRefreshToken, String password) {
        var refreshSession = refreshTokenService.resolveRefreshTokenSession(rawRefreshToken);
        PlatformUser platformUser = platformUserRepository.findById(refreshSession.platformUserId())
                .filter(candidate -> candidate.getStatus() == PlatformUserStatus.ACTIVE)
                .orElseThrow(this::invalidCredentials);
        if (platformUser.getPasswordHash() == null
                || !passwordEncoder.matches(password, platformUser.getPasswordHash())) {
            throw new InvalidCredentialsException("Password is incorrect");
        }
        OffsetDateTime authenticatedAt = OffsetDateTime.now();
        refreshTokenService.revokeTokenFamily(
                refreshSession.tokenFamilyId(), platformUser.getId(), authenticatedAt);
        PlatformRefreshTokenCreationResult replacement =
                refreshTokenService.createRefreshTokenSession(platformUser.getId());
        AuthenticatedSession replacementSession = authenticatedSessionService.createPlatformSession(
                replacement.tokenFamilyId(),
                platformUser.getId(),
                authenticatedAt
        );
        return createAuthenticationResult(
                platformUser,
                replacement.rawRefreshToken(),
                replacement.tokenFamilyId(),
                replacementSession.getAuthenticatedAt()
        );
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeRefreshToken(rawRefreshToken);
        auditService.record(
                null,
                SecurityAuditAction.PLATFORM_REFRESH_TOKEN_REVOKE,
                SecurityAuditOutcome.SUCCESS,
                "PLATFORM_REFRESH_TOKEN",
                null,
                Map.of("reason", "logout")
        );
    }

    private PlatformAuthenticationResult createAuthenticationResult(
            PlatformUser platformUser,
            String rawRefreshToken,
            UUID sessionId,
            OffsetDateTime authenticatedAt
    ) {
        Set<PlatformPermissionCode> permissions =
                permissionService.getActivePermissionsForRoles(
                        platformUser.getRoles()
                );

        String accessToken = sessionId == null
                ? jwtService.generatePlatformAccessToken(platformUser, permissions)
                : jwtService.generatePlatformAccessToken(
                        platformUser, permissions, sessionId, authenticatedAt);

        Set<String> roles = platformUser.getRoles()
                .stream()
                .map(PlatformRole::name)
                .collect(Collectors.toSet());

        PlatformLoginResponse response = new PlatformLoginResponse(
                accessToken,
                "Bearer",
                jwtService.getPlatformAccessTokenExpirationSeconds(),
                platformUser.getId(),
                platformUser.getUsername(),
                roles
        );

        return new PlatformAuthenticationResult(
                response,
                rawRefreshToken
        );
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException(
                INVALID_CREDENTIALS_MESSAGE
        );
    }
}
