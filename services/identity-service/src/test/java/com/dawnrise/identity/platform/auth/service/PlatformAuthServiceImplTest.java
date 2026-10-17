package com.dawnrise.identity.platform.auth.service;

import com.dawnrise.identity.auth.exception.AccountNotActiveException;
import com.dawnrise.identity.auth.exception.InvalidCredentialsException;
import com.dawnrise.identity.auth.lockout.LoginLockoutProperties;
import com.dawnrise.identity.auth.lockout.LoginLockoutService;
import com.dawnrise.identity.auth.security.JwtService;
import com.dawnrise.identity.platform.audit.service.PlatformSecurityAuditService;
import com.dawnrise.identity.auth.session.service.AuthenticatedSessionService;
import com.dawnrise.identity.auth.session.entity.AuthenticatedSession;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenCreationResult;
import com.dawnrise.identity.platform.auth.dto.PlatformLoginRequest;
import com.dawnrise.identity.platform.auth.model.PlatformAuthenticationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenRotationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.service.PlatformRefreshTokenService;
import com.dawnrise.identity.platform.permission.enums.PlatformPermissionCode;
import com.dawnrise.identity.platform.permission.service.PlatformPermissionService;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformRole;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAuthServiceImplTest {

    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private PlatformRefreshTokenService refreshTokenService;
    @Mock
    private PlatformPermissionService permissionService;
    @Mock
    private PlatformSecurityAuditService auditService;
    @Mock
    private AuthenticatedSessionService authenticatedSessionService;

    private PlatformAuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        LoginLockoutProperties lockoutProperties =
                new LoginLockoutProperties();
        lockoutProperties.setFirstFailureThreshold(5);
        lockoutProperties.setEscalatedFailureThreshold(3);
        lockoutProperties.setFirstLockDuration(Duration.ofMinutes(30));
        lockoutProperties.setSecondLockDuration(Duration.ofDays(30));
        lockoutProperties.setFinalLockDuration(Duration.ofDays(365));
        lockoutProperties.setLockedMessagePrefix(
                "Account is locked until "
        );
        lockoutProperties.setLockedMessageSuffix(
                ". Please contact an admin to unlock sooner."
        );

        authService = new PlatformAuthServiceImpl(
                platformUserRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                new LoginLockoutService(lockoutProperties),
                permissionService,
                auditService,
                authenticatedSessionService
        );
        lenient().when(refreshTokenService.createRefreshTokenSession(anyLong()))
                .thenAnswer(invocation -> new PlatformRefreshTokenCreationResult(
                        "refresh-token", UUID.randomUUID()));
        lenient().when(authenticatedSessionService.createPlatformSession(any(), anyLong(), any()))
                .thenAnswer(invocation -> AuthenticatedSession.platformUser(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        lenient().when(authenticatedSessionService.validateAndTouch(any(), any(), anyLong()))
                .thenReturn(new com.dawnrise.identity.auth.session.dto.AuthenticatedSessionValidationResponse(
                        true, OffsetDateTime.now()));
        lenient().when(jwtService.generatePlatformAccessToken(any(), anySet(), any(), any()))
                .thenReturn("access-token");
    }

    @Test
    void login_whenActiveAndPasswordMatches_returnsPlatformTokens() {
        PlatformLoginRequest request = request();
        PlatformUser user = platformUser(PlatformUserStatus.ACTIVE);
        Set<PlatformPermissionCode> permissions =
                Set.of(PlatformPermissionCode.LEAD_VIEW);

        when(platformUserRepository.findByUsernameIgnoreCase("employee01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Employee@123", "encoded-password"))
                .thenReturn(true);
        when(refreshTokenService.createRefreshTokenSession(42L))
                .thenReturn(new PlatformRefreshTokenCreationResult(
                        "raw-refresh", UUID.randomUUID()));
        when(permissionService.getActivePermissionsForRoles(user.getRoles()))
                .thenReturn(permissions);
        when(jwtService.getPlatformAccessTokenExpirationSeconds())
                .thenReturn(900L);

        PlatformAuthenticationResult result = authService.login(request);

        assertEquals("access-token", result.response().accessToken());
        assertEquals("raw-refresh", result.rawRefreshToken());
        assertEquals(42L, result.response().platformUserId());
        assertEquals(Set.of("SALES"), result.response().roles());
        assertNotNull(user.getLastLoginAt());
        verify(refreshTokenService).createRefreshTokenSession(42L);
    }

    @Test
    void login_whenUsernameMissing_usesGenericInvalidCredentials() {
        PlatformLoginRequest request = request();

        when(platformUserRepository.findByUsernameIgnoreCase("employee01"))
                .thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid username or password", exception.getMessage());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_whenPasswordWrong_usesGenericInvalidCredentials() {
        PlatformLoginRequest request = request();
        PlatformUser user = platformUser(PlatformUserStatus.ACTIVE);

        when(platformUserRepository.findByUsernameIgnoreCase("employee01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Employee@123", "encoded-password"))
                .thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(1, user.getFailedLoginAttempts());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_whenAccountPending_rejectsWithoutRefreshToken() {
        PlatformLoginRequest request = request();
        PlatformUser user =
                platformUser(PlatformUserStatus.PENDING_ACTIVATION);

        when(platformUserRepository.findByUsernameIgnoreCase("employee01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Employee@123", "encoded-password"))
                .thenReturn(true);

        assertThrows(
                AccountNotActiveException.class,
                () -> authService.login(request)
        );

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refresh_whenActive_rotatesAndReturnsNewAccessToken() {
        PlatformUser user = platformUser(PlatformUserStatus.ACTIVE);
        Set<PlatformPermissionCode> permissions =
                Set.of(PlatformPermissionCode.LEAD_VIEW);

        when(refreshTokenService.rotateRefreshToken("raw-refresh"))
                .thenReturn(new PlatformRefreshTokenRotationResult(
                        42L,
                        "rotated-refresh"
                ));
        when(platformUserRepository.findById(42L))
                .thenReturn(Optional.of(user));
        when(permissionService.getActivePermissionsForRoles(user.getRoles()))
                .thenReturn(permissions);
        when(jwtService.generatePlatformAccessToken(user, permissions))
                .thenReturn("access-token");
        when(jwtService.getPlatformAccessTokenExpirationSeconds())
                .thenReturn(900L);

        PlatformAuthenticationResult result =
                authService.refresh("raw-refresh");

        assertEquals("access-token", result.response().accessToken());
        assertEquals("rotated-refresh", result.rawRefreshToken());
    }

    private static PlatformLoginRequest request() {
        PlatformLoginRequest request = new PlatformLoginRequest();
        request.setUsername("employee01");
        request.setPassword("Employee@123");
        return request;
    }

    private static PlatformUser platformUser(PlatformUserStatus status) {
        PlatformUser user = new PlatformUser(
                "employee01",
                "Asha",
                "asha@dawnrise.in",
                Set.of(PlatformRole.SALES)
        );
        ReflectionTestUtils.setField(user, "id", 42L);
        ReflectionTestUtils.setField(user, "passwordHash", "encoded-password");
        user.setStatus(status);
        return user;
    }
}
