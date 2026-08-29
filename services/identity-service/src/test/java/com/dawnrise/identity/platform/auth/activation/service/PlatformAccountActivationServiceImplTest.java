package com.dawnrise.identity.platform.auth.activation.service;

import com.dawnrise.identity.auth.activation.config.ActivationTokenProperties;
import com.dawnrise.identity.auth.activation.dto.CompleteAccountActivationRequest;
import com.dawnrise.identity.auth.activation.dto.ResendActivationRequest;
import com.dawnrise.identity.auth.activation.exception.PasswordMismatchException;
import com.dawnrise.identity.auth.activation.security.ActivationTokenCodec;
import com.dawnrise.identity.platform.audit.service.PlatformSecurityAuditService;
import com.dawnrise.identity.platform.auth.activation.entity.PlatformUserActivationToken;
import com.dawnrise.identity.platform.auth.activation.event.PlatformUserActivationRequestedEvent;
import com.dawnrise.identity.platform.auth.activation.repository.PlatformUserActivationTokenRepository;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformRole;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PlatformAccountActivationServiceImplTest {

    @Mock
    private PlatformUserActivationTokenRepository tokenRepository;
    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private ActivationTokenCodec tokenCodec;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PlatformSecurityAuditService auditService;

    private PlatformAccountActivationServiceImpl service;

    @BeforeEach
    void setUp() {
        ActivationTokenProperties properties =
                new ActivationTokenProperties();
        properties.setExpiration(Duration.ofHours(24));
        properties.setResendWindow(Duration.ofHours(24));
        properties.setMaxEmailsPerWindow(3);

        service = new PlatformAccountActivationServiceImpl(
                tokenRepository,
                platformUserRepository,
                tokenCodec,
                properties,
                passwordEncoder,
                eventPublisher,
                auditService
        );
    }

    @Test
    void generateActivationToken_revokesPreviousAndStoresHashOnly() {
        PlatformUser user = platformUser();
        PlatformUserActivationToken previousToken =
                activationToken("old-hash");

        when(platformUserRepository.findById(42L))
                .thenReturn(Optional.of(user));
        when(tokenRepository.countByPlatformUserIdAndCreatedAtAfter(
                eq(42L),
                any()
        )).thenReturn(0L);
        when(tokenRepository
                .findAllByPlatformUserIdAndUsedAtIsNullAndRevokedAtIsNull(
                        42L
                ))
                .thenReturn(List.of(previousToken));
        when(tokenCodec.generateRawToken()).thenReturn("raw-token");
        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");

        String rawToken = service.generateActivationToken(42L);

        assertEquals("raw-token", rawToken);
        assertTrue(previousToken.isRevoked());
        verify(tokenRepository).saveAndFlush(argThat(token ->
                "token-hash".equals(token.getTokenHash())
                        && !"raw-token".equals(token.getTokenHash())
        ));
    }

    @Test
    void completeActivation_whenConfirmationMismatch_throws() {
        CompleteAccountActivationRequest request =
                new CompleteAccountActivationRequest(
                        "raw-token",
                        "Employee@123",
                        "Other@123"
                );

        assertThrows(
                PasswordMismatchException.class,
                () -> service.completeActivation(request)
        );
        verifyNoInteractions(tokenCodec, passwordEncoder);
    }

    @Test
    void completeActivation_whenValid_activatesUserAndMarksTokenUsed() {
        PlatformUser user = platformUser();
        PlatformUserActivationToken token =
                activationToken("token-hash");
        CompleteAccountActivationRequest request =
                new CompleteAccountActivationRequest(
                        "raw-token",
                        "Employee@123",
                        "Employee@123"
                );

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(tokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(platformUserRepository.findById(42L))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode("Employee@123"))
                .thenReturn("encoded-password");

        service.completeActivation(request);

        assertEquals(PlatformUserStatus.ACTIVE, user.getStatus());
        assertEquals("encoded-password", user.getPasswordHash());
        assertTrue(token.isUsed());
    }

    @Test
    void requestActivationResend_whenTokenKnown_publishesEvent() {
        PlatformUser user = platformUser();
        PlatformUserActivationToken token =
                activationToken("token-hash");

        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(tokenRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(token));
        when(platformUserRepository.findById(42L))
                .thenReturn(Optional.of(user));

        service.requestActivationResend(
                new ResendActivationRequest("raw-token")
        );

        verify(eventPublisher).publishEvent(
                new PlatformUserActivationRequestedEvent(42L)
        );
    }

    private static PlatformUser platformUser() {
        PlatformUser user = new PlatformUser(
                "employee01",
                "Asha",
                "asha@dawnrise.in",
                Set.of(PlatformRole.SALES)
        );
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    private static PlatformUserActivationToken activationToken(
            String tokenHash
    ) {
        return new PlatformUserActivationToken(
                42L,
                tokenHash,
                OffsetDateTime.now().plusHours(1)
        );
    }
}
