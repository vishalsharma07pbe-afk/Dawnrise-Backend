package com.dawnrise.identity.auth.refreshtoken.service;

import com.dawnrise.identity.auth.refreshtoken.config.RefreshTokenProperties;
import com.dawnrise.identity.auth.refreshtoken.entity.RefreshToken;
import com.dawnrise.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenRotationResult;
import com.dawnrise.identity.auth.refreshtoken.policy.RefreshTokenSessionLifetimePolicy;
import com.dawnrise.identity.auth.refreshtoken.repository.RefreshTokenRepository;
import com.dawnrise.identity.auth.refreshtoken.security.RefreshTokenCodec;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RefreshTokenCodec tokenCodec;
    @Mock
    private UserRepository userRepository;

    private RefreshTokenServiceImpl service;
    private RefreshTokenProperties tokenProperties;

    @BeforeEach
    void setUp() {
        tokenProperties = new RefreshTokenProperties();
        tokenProperties.setExpiration(Duration.ofDays(30));
        tokenProperties.setAbsoluteSessionLifetime(Duration.ofDays(90));
        tokenProperties.setShortSessionRoles(List.of(
                UserRole.ADMIN,
                UserRole.PRINCIPAL,
                UserRole.HR,
                UserRole.ACCOUNTANT
        ));
        tokenProperties.setShortSessionExpiration(Duration.ofDays(7));
        tokenProperties.setShortSessionAbsoluteLifetime(Duration.ofDays(30));
        tokenProperties.setStandardSessionRoles(List.of(
                UserRole.TEACHER,
                UserRole.STUDENT,
                UserRole.PARENT
        ));
        tokenProperties.setStandardSessionExpiration(Duration.ofDays(30));
        tokenProperties.setStandardSessionAbsoluteLifetime(
                Duration.ofDays(90)
        );

        service = new RefreshTokenServiceImpl(
                refreshTokenRepository,
                tokenCodec,
                tokenProperties,
                new RefreshTokenSessionLifetimePolicy(tokenProperties),
                userRepository
        );
    }

    @Test
    void createRefreshToken_whenUserActive_savesHashedTokenWithFamilyExpiry() {
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(tokenCodec.generateRawToken()).thenReturn("raw-token");
        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");

        String rawToken = service.createRefreshToken(10L);

        assertEquals("raw-token", rawToken);

        ArgumentCaptor<RefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertEquals(10L, tokenCaptor.getValue().getUserId());
        assertEquals("token-hash", tokenCaptor.getValue().getTokenHash());
        assertTrue(tokenCaptor.getValue().getExpiresAt()
                .isAfter(OffsetDateTime.now()));
        assertTrue(tokenCaptor.getValue().getFamilyExpiresAt()
                .isAfter(OffsetDateTime.now().plusDays(89)));
        assertTrue(tokenCaptor.getValue().getFamilyExpiresAt()
                .isBefore(OffsetDateTime.now().plusDays(91)));
    }

    @Test
    void createRefreshToken_whenAdmin_usesShortSessionLifetime() {
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(
                        UserStatus.ACTIVE,
                        Set.of(UserRole.ADMIN)
                )));
        when(tokenCodec.generateRawToken()).thenReturn("raw-token");
        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");

        service.createRefreshToken(10L);

        ArgumentCaptor<RefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        assertTrue(savedToken.getExpiresAt()
                .isAfter(OffsetDateTime.now().plusDays(6)));
        assertTrue(savedToken.getExpiresAt()
                .isBefore(OffsetDateTime.now().plusDays(8)));
        assertTrue(savedToken.getFamilyExpiresAt()
                .isAfter(OffsetDateTime.now().plusDays(29)));
        assertTrue(savedToken.getFamilyExpiresAt()
                .isBefore(OffsetDateTime.now().plusDays(31)));
    }

    @Test
    void createRefreshToken_whenSeparateLogins_createIndependentFamilies() {
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(tokenCodec.generateRawToken())
                .thenReturn("first-raw")
                .thenReturn("second-raw");
        when(tokenCodec.hash("first-raw")).thenReturn("first-hash");
        when(tokenCodec.hash("second-raw")).thenReturn("second-hash");

        service.createRefreshToken(10L);
        service.createRefreshToken(10L);

        ArgumentCaptor<RefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2))
                .save(tokenCaptor.capture());

        RefreshToken first = tokenCaptor.getAllValues().get(0);
        RefreshToken second = tokenCaptor.getAllValues().get(1);

        assertNotEquals(first.getTokenFamilyId(),
                second.getTokenFamilyId());
        assertNotSame(first.getFamilyExpiresAt(),
                second.getFamilyExpiresAt());
        assertTrue(first.getFamilyExpiresAt()
                .isAfter(OffsetDateTime.now().plusDays(89)));
        assertTrue(second.getFamilyExpiresAt()
                .isAfter(OffsetDateTime.now().plusDays(89)));
    }

    @Test
    void createRefreshToken_whenUserInactive_throwsInvalidTokenException() {
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(UserStatus.SUSPENDED)));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.createRefreshToken(10L)
        );
        verifyNoInteractions(tokenCodec);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRefreshToken_whenTokenMissing_throwsInvalidTokenException() {
        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.rotateRefreshToken("raw-token")
        );
    }

    @Test
    void rotateRefreshToken_whenExpired_revokesAndThrowsInvalidTokenException() {
        RefreshToken token = refreshToken(
                OffsetDateTime.now().minusSeconds(1),
                OffsetDateTime.now().plusDays(90)
        );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.rotateRefreshToken("raw-token")
        );
        assertTrue(token.isRevoked());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void rotateRefreshToken_whenReusedToken_revokesTokenFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken reusedToken =
                new RefreshToken(
                        10L,
                        familyId,
                        "token-hash",
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusDays(90)
                );
        reusedToken.rotate(OffsetDateTime.now(), "replacement-hash");

        RefreshToken activeFamilyToken =
                new RefreshToken(
                        10L,
                        familyId,
                        "active-token-hash",
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusDays(90)
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(reusedToken));
        when(refreshTokenRepository
                .findAllByTokenFamilyIdAndRevokedAtIsNull(familyId))
                .thenReturn(List.of(activeFamilyToken));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.rotateRefreshToken("raw-token")
        );
        assertTrue(activeFamilyToken.isRevoked());
    }

    @Test
    void rotateRefreshToken_whenUserInactive_revokesTokenFamilyAndThrows() {
        RefreshToken token =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusDays(90)
                );
        RefreshToken activeFamilyToken =
                new RefreshToken(
                        10L,
                        token.getTokenFamilyId(),
                        "active-token-hash",
                        OffsetDateTime.now().plusHours(1),
                        token.getFamilyExpiresAt()
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(UserStatus.SUSPENDED)));
        when(refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(
                token.getTokenFamilyId()
        )).thenReturn(List.of(activeFamilyToken));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.rotateRefreshToken("raw-token")
        );
        assertTrue(activeFamilyToken.isRevoked());
        verify(tokenCodec, never()).generateRawToken();
    }

    @Test
    void rotateRefreshToken_whenValid_rotatesAndSavesReplacement() {
        RefreshToken token =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusDays(90)
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(tokenCodec.generateRawToken()).thenReturn("replacement-raw");
        when(tokenCodec.hash("replacement-raw"))
                .thenReturn("replacement-hash");

        RefreshTokenRotationResult result =
                service.rotateRefreshToken("raw-token");

        assertEquals(10L, result.userId());
        assertEquals("replacement-raw", result.rawRefreshToken());
        assertTrue(token.isRevoked());
        assertEquals("replacement-hash", token.getReplacedByTokenHash());

        ArgumentCaptor<RefreshToken> replacementCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(replacementCaptor.capture());
        assertEquals(token.getTokenFamilyId(),
                replacementCaptor.getValue().getTokenFamilyId());
        assertEquals(token.getFamilyExpiresAt(),
                replacementCaptor.getValue().getFamilyExpiresAt());
        assertTrue(replacementCaptor.getValue().getExpiresAt()
                .isAfter(OffsetDateTime.now().plusDays(29)));
    }

    @Test
    void rotateRefreshToken_whenUserRoleChanged_usesCurrentRoleForRollingExpiry() {
        RefreshToken token =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusDays(90)
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(
                        UserStatus.ACTIVE,
                        Set.of(UserRole.HR)
                )));
        when(tokenCodec.generateRawToken()).thenReturn("replacement-raw");
        when(tokenCodec.hash("replacement-raw"))
                .thenReturn("replacement-hash");

        service.rotateRefreshToken("raw-token");

        ArgumentCaptor<RefreshToken> replacementCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(replacementCaptor.capture());

        assertTrue(replacementCaptor.getValue().getExpiresAt()
                .isAfter(OffsetDateTime.now().plusDays(6)));
        assertTrue(replacementCaptor.getValue().getExpiresAt()
                .isBefore(OffsetDateTime.now().plusDays(8)));
        assertEquals(token.getFamilyExpiresAt(),
                replacementCaptor.getValue().getFamilyExpiresAt());
    }

    @Test
    void rotateRefreshToken_whenFamilyExpiryNear_capsReplacementExpiry() {
        OffsetDateTime familyExpiresAt =
                OffsetDateTime.now().plusDays(5);
        RefreshToken token =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        familyExpiresAt
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(tokenCodec.generateRawToken()).thenReturn("replacement-raw");
        when(tokenCodec.hash("replacement-raw"))
                .thenReturn("replacement-hash");

        service.rotateRefreshToken("raw-token");

        ArgumentCaptor<RefreshToken> replacementCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(replacementCaptor.capture());

        assertEquals(familyExpiresAt,
                replacementCaptor.getValue().getExpiresAt());
        assertEquals(familyExpiresAt,
                replacementCaptor.getValue().getFamilyExpiresAt());
    }

    @Test
    void rotateRefreshToken_whenBeforeFamilyExpiry_succeeds() {
        RefreshToken token =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusSeconds(30)
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(tokenCodec.generateRawToken()).thenReturn("replacement-raw");
        when(tokenCodec.hash("replacement-raw"))
                .thenReturn("replacement-hash");

        RefreshTokenRotationResult result =
                service.rotateRefreshToken("raw-token");

        assertEquals(10L, result.userId());
        assertEquals("replacement-raw", result.rawRefreshToken());
    }

    @Test
    void rotateRefreshToken_whenFamilyExpired_revokesFamilyAndThrows() {
        RefreshToken token =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().minusSeconds(1)
                );
        RefreshToken activeFamilyToken =
                new RefreshToken(
                        10L,
                        token.getTokenFamilyId(),
                        "active-token-hash",
                        OffsetDateTime.now().plusHours(1),
                        token.getFamilyExpiresAt()
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(
                token.getTokenFamilyId()
        )).thenReturn(List.of(token, activeFamilyToken));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.rotateRefreshToken("raw-token")
        );

        assertTrue(token.isRevoked());
        assertTrue(activeFamilyToken.isRevoked());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void rotateRefreshToken_whenFamilyExpiresNow_revokesFamilyAndThrows() {
        RefreshToken token =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now()
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(
                token.getTokenFamilyId()
        )).thenReturn(List.of(token));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.rotateRefreshToken("raw-token")
        );

        assertTrue(token.isRevoked());
        verify(tokenCodec, never()).generateRawToken();
    }

    @Test
    void revokeRefreshToken_whenBlank_doesNothing() {
        service.revokeRefreshToken(" ");

        verifyNoInteractions(tokenCodec);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void revokeRefreshToken_whenTokenExists_revokesIt() {
        RefreshToken token =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusDays(90)
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));

        service.revokeRefreshToken("raw-token");

        assertTrue(token.isRevoked());
    }

    @Test
    void revokeAllForUser_revokesAllActiveTokens() {
        RefreshToken first =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusDays(90)
                );
        RefreshToken second =
                refreshToken(
                        OffsetDateTime.now().plusHours(1),
                        OffsetDateTime.now().plusDays(90)
                );

        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(10L))
                .thenReturn(List.of(first, second));

        service.revokeAllForUser(10L);

        assertTrue(first.isRevoked());
        assertTrue(second.isRevoked());
    }

    private static RefreshToken refreshToken(
            OffsetDateTime expiresAt,
            OffsetDateTime familyExpiresAt
    ) {
        return new RefreshToken(
                10L,
                UUID.randomUUID(),
                "token-hash",
                expiresAt,
                familyExpiresAt
        );
    }

    private static User user(UserStatus status) {
        return user(status, Set.of(UserRole.TEACHER));
    }

    private static User user(
            UserStatus status,
            Set<UserRole> roles
    ) {
        User user = new User(
                1L,
                "teacher01",
                "Rahul",
                roles
        );
        ReflectionTestUtils.setField(user, "id", 10L);
        user.setStatus(status);
        return user;
    }
}
