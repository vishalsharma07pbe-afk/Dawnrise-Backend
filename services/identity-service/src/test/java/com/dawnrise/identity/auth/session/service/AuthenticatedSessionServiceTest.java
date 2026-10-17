package com.dawnrise.identity.auth.session.service;

import com.dawnrise.identity.auth.model.IdentityType;
import com.dawnrise.identity.auth.session.config.AuthenticatedSessionProperties;
import com.dawnrise.identity.auth.session.entity.AuthenticatedSession;
import com.dawnrise.identity.auth.session.repository.AuthenticatedSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatedSessionServiceTest {

    @Mock
    private AuthenticatedSessionRepository repository;

    private AuthenticatedSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticatedSessionService(
                repository,
                new AuthenticatedSessionProperties()
        );
    }

    @Test
    void retireIfPresent_whenSessionIsAbsent_isIdempotent() {
        UUID sessionId = UUID.randomUUID();
        when(repository.findByIdForUpdate(sessionId)).thenReturn(Optional.empty());

        service.retireIfPresent(
                sessionId,
                IdentityType.ORGANIZATION_USER,
                10L,
                OffsetDateTime.now()
        );

        verify(repository).findByIdForUpdate(sessionId);
    }

    @Test
    void retireIfPresent_whenOwnerDoesNotMatch_doesNotModifySession() {
        UUID sessionId = UUID.randomUUID();
        AuthenticatedSession session = AuthenticatedSession.organizationUser(
                sessionId,
                20L,
                OffsetDateTime.now().minusMinutes(5)
        );
        when(repository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

        service.retireIfPresent(
                sessionId,
                IdentityType.ORGANIZATION_USER,
                10L,
                OffsetDateTime.now()
        );

        assertFalse(session.isLocked());
    }

    @Test
    void retireIfPresent_whenOwnerMatches_usesSuppliedTimestamp() {
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime retiredAt = OffsetDateTime.now().minusSeconds(10);
        AuthenticatedSession session = AuthenticatedSession.platformUser(
                sessionId,
                30L,
                retiredAt.minusMinutes(5)
        );
        when(repository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

        service.retireIfPresent(
                sessionId,
                IdentityType.PLATFORM_USER,
                30L,
                retiredAt
        );

        assertTrue(session.isLocked());
        assertEquals(retiredAt, session.getRetiredAt());
    }
}
