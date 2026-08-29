package com.dawnrise.identity.auth.passwordreset.service;

import com.dawnrise.identity.auth.activation.exception.PasswordMismatchException;
import com.dawnrise.identity.auth.exception.PasswordChangeNotAllowedException;
import com.dawnrise.identity.auth.activation.security.ActivationTokenCodec;
import com.dawnrise.identity.auth.passwordreset.config.PasswordResetTokenProperties;
import com.dawnrise.identity.auth.passwordreset.dto.CompletePasswordResetRequest;
import com.dawnrise.identity.auth.passwordreset.dto.PasswordResetRequest;
import com.dawnrise.identity.auth.passwordreset.entity.UserPasswordResetToken;
import com.dawnrise.identity.auth.passwordreset.event.UserPasswordResetRequestedEvent;
import com.dawnrise.identity.auth.passwordreset.exception.InvalidPasswordResetTokenException;
import com.dawnrise.identity.auth.passwordreset.repository.UserPasswordResetTokenRepository;
import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenService;
import com.dawnrise.identity.organization.entity.Organization;
import com.dawnrise.identity.organization.repository.OrganizationRepository;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private UserPasswordResetTokenRepository resetTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ActivationTokenCodec tokenCodec;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RefreshTokenService refreshTokenService;

    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        PasswordResetTokenProperties tokenProperties =
                new PasswordResetTokenProperties();

        tokenProperties.setExpiration(
                Duration.ofHours(1)
        );
        tokenProperties.setRequestWindow(
                Duration.ofHours(24)
        );
        tokenProperties.setMaxEmailsPerWindow(3);

        service = new PasswordResetServiceImpl(
                resetTokenRepository,
                userRepository,
                organizationRepository,
                tokenCodec,
                tokenProperties,
                passwordEncoder,
                eventPublisher,
                refreshTokenService
        );
    }

    @Test
    void requestPasswordReset_whenActiveUserExists_publishesResetEvent() {
        PasswordResetRequest request =
                new PasswordResetRequest(
                        "SCHOOL001",
                        "teacher@dawnrise.com"
                );

        User user = user(UserStatus.ACTIVE);
        mockOrganizationLookup("SCHOOL001", 1L);

        when(userRepository
                .findByOrganizationIdAndEmailIgnoreCase(
                        1L,
                        "teacher@dawnrise.com"
                ))
                .thenReturn(Optional.of(user));

        service.requestPasswordReset(request);

        verify(eventPublisher).publishEvent(
                new UserPasswordResetRequestedEvent(10L)
        );
    }

    @Test
    void requestPasswordReset_whenSchoolMissing_finishesSilently() {
        PasswordResetRequest request =
                new PasswordResetRequest(
                        "UNKNOWN",
                        "teacher@dawnrise.com"
                );

        when(organizationRepository
                .findBySchoolCodeIgnoreCase("UNKNOWN"))
                .thenReturn(Optional.empty());

        service.requestPasswordReset(request);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void requestPasswordReset_whenUserMissing_finishesSilently() {
        PasswordResetRequest request =
                new PasswordResetRequest(
                        "SCHOOL001",
                        "missing@dawnrise.com"
                );

        mockOrganizationLookup("SCHOOL001", 1L);

        when(userRepository
                .findByOrganizationIdAndEmailIgnoreCase(
                        1L,
                        "missing@dawnrise.com"
                ))
                .thenReturn(Optional.empty());

        service.requestPasswordReset(request);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void requestPasswordReset_whenUserInactive_doesNotPublishEvent() {
        PasswordResetRequest request =
                new PasswordResetRequest(
                        "SCHOOL001",
                        "teacher@dawnrise.com"
                );

        mockOrganizationLookup("SCHOOL001", 1L);

        when(userRepository
                .findByOrganizationIdAndEmailIgnoreCase(
                        1L,
                        "teacher@dawnrise.com"
                ))
                .thenReturn(Optional.of(
                        user(UserStatus.PENDING_ACTIVATION)
                ));

        service.requestPasswordReset(request);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void generatePasswordResetToken_whenEmailLimitReached_throwsException() {
        User user = user(UserStatus.ACTIVE);

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        when(resetTokenRepository
                .countByUserIdAndCreatedAtAfter(
                        eq(10L),
                        any(OffsetDateTime.class)
                ))
                .thenReturn(3L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.generatePasswordResetToken(10L)
        );

        assertEquals(
                "Password reset email rate limit exceeded",
                exception.getMessage()
        );

        verify(resetTokenRepository, never())
                .save(any());
    }

    @Test
    void generatePasswordResetToken_afterCompletedReset_usesFreshEmailWindow() {
        User user = user(UserStatus.ACTIVE);
        user.resetPassword("recently-reset-password-hash");
        OffsetDateTime passwordChangedAt = user.getPasswordChangedAt();

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));
        when(resetTokenRepository
                .countByUserIdAndCreatedAtAfter(
                        10L,
                        passwordChangedAt
                ))
                .thenReturn(0L);
        when(resetTokenRepository
                .findAllByUserIdAndUsedAtIsNullAndRevokedAtIsNull(
                        10L
                ))
                .thenReturn(List.of());
        when(tokenCodec.generateRawToken())
                .thenReturn("new-raw-token");
        when(tokenCodec.hash("new-raw-token"))
                .thenReturn("new-token-hash");

        assertEquals(
                "new-raw-token",
                service.generatePasswordResetToken(10L)
        );

        verify(resetTokenRepository)
                .countByUserIdAndCreatedAtAfter(
                        10L,
                        passwordChangedAt
                );
        verify(resetTokenRepository).save(any());
    }

    @Test
    void generatePasswordResetToken_whenValid_revokesPreviousAndSavesHashedToken() {
        User user = user(UserStatus.ACTIVE);

        UserPasswordResetToken previousToken =
                new UserPasswordResetToken(
                        10L,
                        "old-token-hash",
                        OffsetDateTime.now().plusHours(1)
                );

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        when(resetTokenRepository
                .countByUserIdAndCreatedAtAfter(
                        eq(10L),
                        any(OffsetDateTime.class)
                ))
                .thenReturn(2L);

        when(resetTokenRepository
                .findAllByUserIdAndUsedAtIsNullAndRevokedAtIsNull(
                        10L
                ))
                .thenReturn(List.of(previousToken));

        when(tokenCodec.generateRawToken())
                .thenReturn("raw-token");

        when(tokenCodec.hash("raw-token"))
                .thenReturn("new-token-hash");

        String rawToken =
                service.generatePasswordResetToken(10L);

        assertEquals("raw-token", rawToken);
        assertFalse(
                previousToken.isValidAt(
                        OffsetDateTime.now()
                )
        );

        ArgumentCaptor<UserPasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        UserPasswordResetToken.class
                );

        verify(resetTokenRepository)
                .save(tokenCaptor.capture());

        assertEquals(
                10L,
                tokenCaptor.getValue().getUserId()
        );
    }

    @Test
    void isPasswordResetTokenValid_whenTokenMatchesActiveUser_returnsTrue() {
        UserPasswordResetToken token =
                new UserPasswordResetToken(
                        10L,
                        "token-hash",
                        OffsetDateTime.now().plusHours(1)
                );

        when(tokenCodec.hash("raw-token"))
                .thenReturn("token-hash");

        when(resetTokenRepository
                .findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(
                        user(UserStatus.ACTIVE)
                ));

        assertTrue(
                service.isPasswordResetTokenValid(
                        "raw-token"
                )
        );
    }

    @Test
    void isPasswordResetTokenValid_whenTokenExpired_returnsFalse() {
        UserPasswordResetToken token =
                new UserPasswordResetToken(
                        10L,
                        "token-hash",
                        OffsetDateTime.now().minusSeconds(1)
                );

        when(tokenCodec.hash("raw-token"))
                .thenReturn("token-hash");

        when(resetTokenRepository
                .findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));

        assertFalse(
                service.isPasswordResetTokenValid(
                        "raw-token"
                )
        );

        verify(userRepository, never())
                .findById(anyLong());
    }

    @Test
    void completePasswordReset_whenPasswordMismatch_throwsException() {
        CompletePasswordResetRequest request =
                new CompletePasswordResetRequest(
                        "raw-token",
                        "Teacher@123",
                        "Teacher@456"
                );

        PasswordMismatchException exception =
                assertThrows(
                        PasswordMismatchException.class,
                        () -> service.completePasswordReset(
                                request
                        )
                );

        assertEquals(
                "Password and confirmation do not match",
                exception.getMessage()
        );

        verifyNoInteractions(tokenCodec);
    }

    @Test
    void completePasswordReset_whenPasswordChangedRecently_stillResets() {
        User user = user(UserStatus.ACTIVE);
        user.resetPassword("recent-password");

        UserPasswordResetToken token =
                new UserPasswordResetToken(
                        10L,
                        "token-hash",
                        OffsetDateTime.now().plusHours(1)
                );

        when(tokenCodec.hash("raw-token"))
                .thenReturn("token-hash");

        when(resetTokenRepository
                .findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("Teacher@123"))
                .thenReturn("encoded-new-password");

        service.completePasswordReset(
                completeRequest()
        );

        assertEquals(
                "encoded-new-password",
                user.getPasswordHash()
        );

        assertFalse(
                token.isValidAt(OffsetDateTime.now())
        );

        verify(refreshTokenService)
                .revokeAllForUser(10L);
    }

    @Test
    void completePasswordReset_whenPasswordMatchesCurrent_rejectsWithoutUsingToken() {
        User user = user(UserStatus.ACTIVE);
        UserPasswordResetToken token =
                new UserPasswordResetToken(
                        10L,
                        "token-hash",
                        OffsetDateTime.now().plusHours(1)
                );

        when(tokenCodec.hash("raw-token"))
                .thenReturn("token-hash");
        when(resetTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(
                "Teacher@123",
                "old-password"
        )).thenReturn(true);

        PasswordChangeNotAllowedException exception =
                assertThrows(
                        PasswordChangeNotAllowedException.class,
                        () -> service.completePasswordReset(
                                completeRequest()
                        )
                );

        assertEquals(
                "New password must be different from the current password",
                exception.getMessage()
        );
        assertTrue(token.isValidAt(OffsetDateTime.now()));
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void completePasswordReset_whenValid_hashesPasswordAndMarksTokenUsed() {
        User user = user(UserStatus.ACTIVE);

        UserPasswordResetToken token =
                new UserPasswordResetToken(
                        10L,
                        "token-hash",
                        OffsetDateTime.now().plusHours(1)
                );

        when(tokenCodec.hash("raw-token"))
                .thenReturn("token-hash");

        when(resetTokenRepository
                .findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("Teacher@123"))
                .thenReturn("encoded-new-password");

        service.completePasswordReset(
                completeRequest()
        );

        assertEquals(
                "encoded-new-password",
                user.getPasswordHash()
        );

        assertNotNull(user.getPasswordChangedAt());

        assertFalse(
                token.isValidAt(OffsetDateTime.now())
        );

        verify(refreshTokenService)
                .revokeAllForUser(10L);
    }

    @Test
    void completePasswordReset_whenTokenMissing_throwsInvalidTokenException() {
        when(tokenCodec.hash("raw-token"))
                .thenReturn("token-hash");

        when(resetTokenRepository
                .findByTokenHash("token-hash"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> service.completePasswordReset(
                        completeRequest()
                )
        );

        verifyNoInteractions(passwordEncoder);
    }

    private void mockOrganizationLookup(
            String schoolCode,
            Long organizationId
    ) {
        Organization organization =
                mock(Organization.class);

        when(organization.getId())
                .thenReturn(organizationId);

        when(organizationRepository
                .findBySchoolCodeIgnoreCase(schoolCode))
                .thenReturn(Optional.of(organization));
    }

    private static CompletePasswordResetRequest completeRequest() {
        return new CompletePasswordResetRequest(
                "raw-token",
                "Teacher@123",
                "Teacher@123"
        );
    }

    private static User user(UserStatus status) {
        User user = new User(
                1L,
                "teacher01",
                "Rahul",
                Set.of(UserRole.TEACHER)
        );

        ReflectionTestUtils.setField(
                user,
                "id",
                10L
        );

        ReflectionTestUtils.setField(
                user,
                "passwordHash",
                "old-password"
        );

        user.setEmail("teacher@dawnrise.com");
        user.setStatus(status);

        return user;
    }
}
