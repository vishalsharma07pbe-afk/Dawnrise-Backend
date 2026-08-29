package com.dawnrise.identity.platform.auth.refreshtoken.service;

import com.dawnrise.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.dawnrise.identity.auth.refreshtoken.security.RefreshTokenCodec;
import com.dawnrise.identity.platform.auth.refreshtoken.config.PlatformRefreshTokenProperties;
import com.dawnrise.identity.platform.auth.refreshtoken.entity.PlatformRefreshToken;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenRotationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.repository.PlatformRefreshTokenRepository;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformRole;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
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
class PlatformRefreshTokenServiceImplTest {

    @Mock
    private PlatformRefreshTokenRepository refreshTokenRepository;
    @Mock
    private RefreshTokenCodec tokenCodec;
    @Mock
    private PlatformUserRepository platformUserRepository;

    private PlatformRefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        PlatformRefreshTokenProperties properties =
                new PlatformRefreshTokenProperties();
        properties.setExpiration(Duration.ofDays(7));
        properties.setAbsoluteSessionLifetime(Duration.ofDays(30));

        service = new PlatformRefreshTokenServiceImpl(
                refreshTokenRepository,
                tokenCodec,
                properties,
                platformUserRepository
        );
    }

    @Test
    void createRefreshToken_storesHashOnly() {
        when(platformUserRepository.findById(42L))
                .thenReturn(Optional.of(user(PlatformUserStatus.ACTIVE)));
        when(tokenCodec.generateRawToken()).thenReturn("raw-token");
        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");

        String rawToken = service.createRefreshToken(42L);

        assertEquals("raw-token", rawToken);

        ArgumentCaptor<PlatformRefreshToken> captor =
                ArgumentCaptor.forClass(PlatformRefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals("token-hash", captor.getValue().getTokenHash());
        assertNotEquals("raw-token", captor.getValue().getTokenHash());
    }

    @Test
    void rotateRefreshToken_rotatesAndPreservesFamily() {
        UUID familyId = UUID.randomUUID();
        PlatformRefreshToken existingToken = new PlatformRefreshToken(
                42L,
                familyId,
                "token-hash",
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusDays(30)
        );
        PlatformUser user = user(PlatformUserStatus.ACTIVE);

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(refreshTokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(existingToken));
        when(platformUserRepository.findById(42L))
                .thenReturn(Optional.of(user));
        when(tokenCodec.generateRawToken()).thenReturn("replacement-raw");
        when(tokenCodec.hash("replacement-raw"))
                .thenReturn("replacement-hash");

        PlatformRefreshTokenRotationResult result =
                service.rotateRefreshToken("raw-token");

        assertEquals(42L, result.platformUserId());
        assertEquals("replacement-raw", result.rawRefreshToken());
        assertTrue(existingToken.isRevoked());

        ArgumentCaptor<PlatformRefreshToken> captor =
                ArgumentCaptor.forClass(PlatformRefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals(familyId, captor.getValue().getTokenFamilyId());
        assertEquals("replacement-hash", captor.getValue().getTokenHash());
    }

    @Test
    void rotateRefreshToken_whenReused_revokesFamily() {
        UUID familyId = UUID.randomUUID();
        PlatformRefreshToken reusedToken = new PlatformRefreshToken(
                42L,
                familyId,
                "token-hash",
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusDays(30)
        );
        reusedToken.rotate(OffsetDateTime.now(), "replacement-hash");

        PlatformRefreshToken activeFamilyToken = new PlatformRefreshToken(
                42L,
                familyId,
                "active-token-hash",
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusDays(30)
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

    private static PlatformUser user(PlatformUserStatus status) {
        PlatformUser user = new PlatformUser(
                "employee01",
                "Asha",
                "asha@dawnrise.in",
                Set.of(PlatformRole.SALES)
        );
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setStatus(status);
        return user;
    }
}
