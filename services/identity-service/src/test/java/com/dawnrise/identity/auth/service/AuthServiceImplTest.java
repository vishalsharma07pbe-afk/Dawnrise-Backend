package com.dawnrise.identity.auth.service;

import com.dawnrise.identity.auth.config.PasswordChangeProperties;
import com.dawnrise.identity.auth.dto.ChangePasswordRequest;
import com.dawnrise.identity.auth.dto.LoginRequest;
import com.dawnrise.identity.auth.dto.LoginResponse;
import com.dawnrise.identity.auth.activation.exception.PasswordMismatchException;
import com.dawnrise.identity.auth.exception.AccountLockedException;
import com.dawnrise.identity.auth.exception.AccountNotActiveException;
import com.dawnrise.identity.auth.exception.InvalidCredentialsException;
import com.dawnrise.identity.auth.exception.PasswordChangeNotAllowedException;
import com.dawnrise.identity.auth.lockout.LoginLockoutProperties;
import com.dawnrise.identity.auth.lockout.LoginLockoutService;
import com.dawnrise.identity.auth.model.AuthenticationResult;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenRotationResult;
import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.auth.security.JwtService;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.permission.service.PermissionService;
import com.dawnrise.identity.organization.entity.Organization;
import com.dawnrise.identity.organization.repository.OrganizationRepository;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Set<PermissionCode> ACTIVE_PERMISSIONS =
            Set.of(PermissionCode.PROFILE_VIEW_SELF);

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private SecurityAuditService auditService;

    private AuthServiceImpl authService;
    private PasswordChangeProperties passwordChangeProperties;

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
        passwordChangeProperties = new PasswordChangeProperties();
        passwordChangeProperties.setCooldown(Duration.ofHours(24));

        authService = new AuthServiceImpl(
                userRepository,
                organizationRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                new LoginLockoutService(lockoutProperties),
                passwordChangeProperties,
                permissionService,
                auditService
        );

        Organization organization = new Organization(
                1L,
                "SCH001",
                "Test School",
                "school@example.com"
        );
        lenient().when(organizationRepository
                        .findBySchoolCodeIgnoreCase("SCH001"))
                .thenReturn(Optional.of(organization));
    }

    @Test
    void login_whenUserMissing_throwsInvalidCredentialsException() {
        LoginRequest request = request();

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid username or password", exception.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generateAccessToken(
                any(User.class),
                anySet()
        );
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_whenEmailProvided_usesOrganizationEmailLookup() {
        LoginRequest request = request();
        request.setUsernameOrEmail("teacher@example.com");

        when(userRepository.findByOrganizationIdAndEmailIgnoreCase(
                1L,
                "teacher@example.com"
        )).thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(userRepository).findByOrganizationIdAndEmailIgnoreCase(
                1L,
                "teacher@example.com"
        );
        verify(userRepository, never())
                .findByOrganizationIdAndUsername(anyLong(), anyString());
    }

    @Test
    void login_whenSchoolCodeMissing_throwsInvalidCredentialsWithoutAudit() {
        LoginRequest request = request();
        request.setSchoolCode("UNKNOWN");

        when(organizationRepository.findBySchoolCodeIgnoreCase("UNKNOWN"))
                .thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid username or password", exception.getMessage());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(auditService);
    }

    @Test
    void login_whenPasswordDoesNotMatch_throwsInvalidCredentialsException() {
        LoginRequest request = request();
        User user = user(UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(1, user.getFailedLoginAttempts());
        verify(auditService).record(
                eq(1L),
                eq(10L),
                eq(SecurityAuditAction.LOGIN_FAILURE),
                eq(SecurityAuditOutcome.FAILURE),
                eq("USER"),
                eq(10L),
                anyMap()
        );
        verify(jwtService, never()).generateAccessToken(
                any(User.class),
                anySet()
        );
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_whenFifthBadPassword_locksAccountForThirtyMinutes() {
        LoginRequest request = request();
        User user = user(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(4);

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(false);

        AccountLockedException exception = assertThrows(
                AccountLockedException.class,
                () -> authService.login(request)
        );

        assertTrue(exception.getMessage().startsWith(
                "Account is locked until "
        ));
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(1, user.getLoginLockLevel());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(
                OffsetDateTime.now().plusMinutes(29)
        ));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_whenAccountLocked_blocksLoginWithoutCheckingPassword() {
        LoginRequest request = request();
        User user = user(UserStatus.ACTIVE);
        user.setLockedUntil(OffsetDateTime.now().plusMinutes(10));

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.of(user));

        assertThrows(
                AccountLockedException.class,
                () -> authService.login(request)
        );

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_whenLockExpired_allowsSuccessfulLoginAndClearsFailures() {
        LoginRequest request = request();
        User user = user(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(2);
        user.setLockedUntil(OffsetDateTime.now().minusMinutes(1));

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(true);
        when(refreshTokenService.createRefreshToken(10L))
                .thenReturn("refresh-token");
        when(permissionService.getActivePermissionsForRoles(user.getRoles()))
                .thenReturn(ACTIVE_PERMISSIONS);
        when(jwtService.generateAccessToken(user, ACTIVE_PERMISSIONS))
                .thenReturn("jwt-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthenticationResult result = authService.login(request);

        assertEquals("jwt-token", result.response().getAccessToken());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void login_afterFirstLock_whenThirdBadPassword_locksAccountForOneMonth() {
        LoginRequest request = request();
        User user = user(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "loginLockLevel", 1);
        user.setFailedLoginAttempts(2);

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(false);

        assertThrows(
                AccountLockedException.class,
                () -> authService.login(request)
        );

        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(2, user.getLoginLockLevel());
        assertTrue(user.getLockedUntil().isAfter(
                OffsetDateTime.now().plusDays(27)
        ));
    }

    @Test
    void login_afterSecondLock_whenThirdBadPassword_locksAccountForOneYear() {
        LoginRequest request = request();
        User user = user(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "loginLockLevel", 2);
        user.setFailedLoginAttempts(2);

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(false);

        assertThrows(
                AccountLockedException.class,
                () -> authService.login(request)
        );

        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(3, user.getLoginLockLevel());
        assertTrue(user.getLockedUntil().isAfter(
                OffsetDateTime.now().plusDays(360)
        ));
    }

    @Test
    void login_whenAccountNotActive_throwsAccountNotActiveException() {
        LoginRequest request = request();
        User user = user(UserStatus.PENDING_ACTIVATION);

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(true);

        AccountNotActiveException exception = assertThrows(
                AccountNotActiveException.class,
                () -> authService.login(request)
        );

        assertEquals("Account is not active", exception.getMessage());
        verify(jwtService, never()).generateAccessToken(
                any(User.class),
                anySet()
        );
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_whenValid_returnsTokensAndUpdatesLoginState() {
        LoginRequest request = request();
        User user = user(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(3);

        when(userRepository.findByOrganizationIdAndUsername(1L, "teacher01"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(true);
        when(refreshTokenService.createRefreshToken(10L))
                .thenReturn("refresh-token");
        when(permissionService.getActivePermissionsForRoles(user.getRoles()))
                .thenReturn(ACTIVE_PERMISSIONS);
        when(jwtService.generateAccessToken(user, ACTIVE_PERMISSIONS))
                .thenReturn("jwt-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthenticationResult result = authService.login(request);
        LoginResponse response = result.response();

        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("refresh-token", result.rawRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(10L, response.getUserId());
        assertEquals(1L, response.getOrganizationId());
        assertEquals("teacher01", response.getUsername());
        assertEquals(Set.of("TEACHER", "HR"), response.getRoles());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNotNull(user.getLastLoginAt());
        verify(refreshTokenService).createRefreshToken(10L);
    }

    @Test
    void refresh_whenUserMissing_throwsResourceNotFoundException() {
        when(refreshTokenService.rotateRefreshToken("refresh-token"))
                .thenReturn(new RefreshTokenRotationResult(
                        10L,
                        "rotated-refresh-token"
                ));
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> authService.refresh("refresh-token")
        );
    }

    @Test
    void refresh_whenUserInactive_revokesAllAndThrowsAccountNotActiveException() {
        User user = user(UserStatus.SUSPENDED);

        when(refreshTokenService.rotateRefreshToken("refresh-token"))
                .thenReturn(new RefreshTokenRotationResult(
                        10L,
                        "rotated-refresh-token"
                ));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        AccountNotActiveException exception = assertThrows(
                AccountNotActiveException.class,
                () -> authService.refresh("refresh-token")
        );

        assertEquals("Account is not active", exception.getMessage());
        verify(refreshTokenService).revokeAllForUser(10L);
        verify(jwtService, never()).generateAccessToken(
                any(User.class),
                anySet()
        );
    }

    @Test
    void refresh_whenValid_returnsNewAccessTokenWithRotatedRefreshToken() {
        User user = user(UserStatus.ACTIVE);

        when(refreshTokenService.rotateRefreshToken("refresh-token"))
                .thenReturn(new RefreshTokenRotationResult(
                        10L,
                        "rotated-refresh-token"
                ));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(permissionService.getActivePermissionsForRoles(user.getRoles()))
                .thenReturn(ACTIVE_PERMISSIONS);
        when(jwtService.generateAccessToken(user, ACTIVE_PERMISSIONS))
                .thenReturn("jwt-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthenticationResult result = authService.refresh("refresh-token");

        assertEquals("jwt-token", result.response().getAccessToken());
        assertEquals("rotated-refresh-token", result.rawRefreshToken());
    }

    @Test
    void logout_revokesRefreshToken() {
        authService.logout("refresh-token");

        verify(refreshTokenService).revokeRefreshToken("refresh-token");
    }

    @Test
    void changePassword_whenCurrentPasswordWrong_throwsInvalidCredentialsException() {
        ChangePasswordRequest request = changePasswordRequest();
        User user = user(UserStatus.ACTIVE);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.changePassword(10L, request)
        );

        assertEquals("Current password is incorrect", exception.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void changePassword_whenConfirmationMismatch_throwsPasswordMismatchException() {
        ChangePasswordRequest request = changePasswordRequest();
        request.setConfirmPassword("Other@123");

        PasswordMismatchException exception = assertThrows(
                PasswordMismatchException.class,
                () -> authService.changePassword(10L, request)
        );

        assertEquals(
                "Password and confirmation do not match",
                exception.getMessage()
        );
        verifyNoInteractions(userRepository);
    }

    @Test
    void changePassword_whenCooldownActive_throwsPasswordChangeNotAllowedException() {
        ChangePasswordRequest request = changePasswordRequest();
        User user = user(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(
                user,
                "passwordChangedAt",
                OffsetDateTime.now().minusHours(2)
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(true);

        assertThrows(
                PasswordChangeNotAllowedException.class,
                () -> authService.changePassword(10L, request)
        );

        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void changePassword_whenNewPasswordMatchesCurrent_throwsPasswordChangeNotAllowedException() {
        ChangePasswordRequest request = changePasswordRequest();
        User user = user(UserStatus.ACTIVE);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), eq("encoded-password")))
                .thenReturn(true);

        PasswordChangeNotAllowedException exception = assertThrows(
                PasswordChangeNotAllowedException.class,
                () -> authService.changePassword(10L, request)
        );

        assertEquals(
                "New password must be different from the current password",
                exception.getMessage()
        );
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void changePassword_whenValid_updatesPasswordAndRevokesSessions() {
        ChangePasswordRequest request = changePasswordRequest();
        User user = user(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(
                user,
                "passwordChangedAt",
                OffsetDateTime.now().minusHours(25)
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Teacher@123", "encoded-password"))
                .thenReturn(true);
        when(passwordEncoder.encode("NewTeacher@123"))
                .thenReturn("encoded-new-password");

        authService.changePassword(10L, request);

        assertEquals("encoded-new-password", user.getPasswordHash());
        assertNotNull(user.getPasswordChangedAt());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(refreshTokenService).revokeAllForUser(10L);
    }

    private static LoginRequest request() {
        LoginRequest request = new LoginRequest();
        request.setSchoolCode("SCH001");
        request.setUsernameOrEmail("teacher01");
        request.setPassword("Teacher@123");
        return request;
    }

    private static ChangePasswordRequest changePasswordRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Teacher@123");
        request.setNewPassword("NewTeacher@123");
        request.setConfirmPassword("NewTeacher@123");
        return request;
    }

    private static User user(UserStatus status) {
        User user = new User(
                1L,
                "teacher01",
                "Rahul",
                Set.of(UserRole.TEACHER, UserRole.HR)
        );
        ReflectionTestUtils.setField(user, "id", 10L);
        ReflectionTestUtils.setField(user, "passwordHash", "encoded-password");
        user.setStatus(status);
        return user;
    }
}
