package com.edusphere.identity.platform.auth.service;

import com.edusphere.identity.auth.exception.AccountNotActiveException;
import com.edusphere.identity.auth.exception.InvalidCredentialsException;
import com.edusphere.identity.auth.lockout.LoginLockoutService;
import com.edusphere.identity.auth.security.JwtService;
import com.edusphere.identity.common.exception.ResourceNotFoundException;
import com.edusphere.identity.platform.audit.service.PlatformSecurityAuditService;
import com.edusphere.identity.platform.auth.dto.PlatformLoginRequest;
import com.edusphere.identity.platform.auth.dto.PlatformLoginResponse;
import com.edusphere.identity.platform.auth.model.PlatformAuthenticationResult;
import com.edusphere.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenRotationResult;
import com.edusphere.identity.platform.auth.refreshtoken.service.PlatformRefreshTokenService;
import com.edusphere.identity.platform.permission.enums.PlatformPermissionCode;
import com.edusphere.identity.platform.permission.service.PlatformPermissionService;
import com.edusphere.identity.platform.user.entity.PlatformUser;
import com.edusphere.identity.platform.user.enums.PlatformRole;
import com.edusphere.identity.platform.user.enums.PlatformUserStatus;
import com.edusphere.identity.platform.user.repository.PlatformUserRepository;
import com.edusphere.identity.securityaudit.enums.SecurityAuditAction;
import com.edusphere.identity.securityaudit.enums.SecurityAuditOutcome;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    public PlatformAuthServiceImpl(
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PlatformRefreshTokenService refreshTokenService,
            LoginLockoutService loginLockoutService,
            PlatformPermissionService permissionService,
            PlatformSecurityAuditService auditService
    ) {
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginLockoutService = loginLockoutService;
        this.permissionService = permissionService;
        this.auditService = auditService;
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

        String rawRefreshToken =
                refreshTokenService.createRefreshToken(
                        platformUser.getId()
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
                rawRefreshToken
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
                rotationResult.rawRefreshToken()
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
            String rawRefreshToken
    ) {
        Set<PlatformPermissionCode> permissions =
                permissionService.getActivePermissionsForRoles(
                        platformUser.getRoles()
                );

        String accessToken =
                jwtService.generatePlatformAccessToken(
                        platformUser,
                        permissions
                );

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
