package com.dawnrise.identity.auth.session.service;

import com.dawnrise.identity.auth.model.IdentityType;
import com.dawnrise.identity.auth.session.config.AuthenticatedSessionProperties;
import com.dawnrise.identity.auth.session.dto.AuthenticatedSessionValidationResponse;
import com.dawnrise.identity.auth.session.entity.AuthenticatedSession;
import com.dawnrise.identity.auth.session.exception.AuthenticatedSessionLockedException;
import com.dawnrise.identity.auth.session.repository.AuthenticatedSessionRepository;
import com.dawnrise.identity.auth.parentsession.exception.RecentParentAuthenticationRequiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthenticatedSessionService {

    private final AuthenticatedSessionRepository sessions;
    private final AuthenticatedSessionProperties properties;

    public AuthenticatedSessionService(
            AuthenticatedSessionRepository sessions,
            AuthenticatedSessionProperties properties
    ) {
        this.sessions = sessions;
        this.properties = properties;
    }

    @Transactional
    public AuthenticatedSession createOrganizationSession(
            UUID id,
            Long userId,
            OffsetDateTime authenticatedAt
    ) {
        return sessions.save(AuthenticatedSession.organizationUser(id, userId, authenticatedAt));
    }

    @Transactional
    public AuthenticatedSession createPlatformSession(
            UUID id,
            Long userId,
            OffsetDateTime authenticatedAt
    ) {
        return sessions.save(AuthenticatedSession.platformUser(id, userId, authenticatedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = AuthenticatedSessionLockedException.class)
    public AuthenticatedSessionValidationResponse validateAndTouch(
            UUID id,
            IdentityType identityType,
            Long ownerId
    ) {
        AuthenticatedSession session = ownedForUpdate(id, identityType, ownerId);
        OffsetDateTime now = OffsetDateTime.now();
        if (session.isLocked()
                || !session.getLastActivityAt().plus(properties.getInactivityTimeout()).isAfter(now)) {
            session.lock(now);
            throw new AuthenticatedSessionLockedException("Workspace is locked");
        }
        session.touch(now);
        return new AuthenticatedSessionValidationResponse(true, session.getAuthenticatedAt());
    }

    @Transactional
    public void lock(UUID id, IdentityType identityType, Long ownerId) {
        ownedForUpdate(id, identityType, ownerId).lock(OffsetDateTime.now());
    }

    @Transactional(noRollbackFor = AuthenticatedSessionLockedException.class)
    public boolean isLocked(UUID id, IdentityType identityType, Long ownerId) {
        AuthenticatedSession session = ownedForUpdate(id, identityType, ownerId);
        OffsetDateTime now = OffsetDateTime.now();
        if (!session.isLocked()
                && !session.getLastActivityAt().plus(properties.getInactivityTimeout()).isAfter(now)) {
            session.lock(now);
        }
        return session.isLocked();
    }

    @Transactional(readOnly = true)
    public void requireRecentAuthentication(UUID id, IdentityType identityType, Long ownerId) {
        AuthenticatedSession session = sessions.findById(id)
                .filter(candidate -> candidate.isOwnedBy(identityType, ownerId))
                .orElseThrow(() -> new AuthenticatedSessionLockedException("Session is unavailable"));
        if (session.isLocked()
                || session.getAuthenticatedAt().plus(properties.getRecentAuthentication()).isBefore(OffsetDateTime.now())) {
            throw new RecentParentAuthenticationRequiredException("Recent authentication is required");
        }
    }

    @Transactional
    public void retire(UUID id, IdentityType identityType, Long ownerId) {
        ownedForUpdate(id, identityType, ownerId).retire(OffsetDateTime.now());
    }

    @Transactional
    public void retireIfPresent(
            UUID id,
            IdentityType identityType,
            Long ownerId,
            OffsetDateTime retiredAt
    ) {
        sessions.findByIdForUpdate(id)
                .filter(session -> session.isOwnedBy(identityType, ownerId))
                .ifPresent(session -> session.retire(retiredAt));
    }

    @Transactional
    public void retireAllForOrganizationUser(Long userId, OffsetDateTime now) {
        sessions.findAllByUserIdForUpdate(userId).forEach(session -> session.retire(now));
    }

    @Transactional
    public void retireAllForPlatformUser(Long userId, OffsetDateTime now) {
        sessions.findAllByPlatformUserIdForUpdate(userId).forEach(session -> session.retire(now));
    }

    private AuthenticatedSession ownedForUpdate(
            UUID id,
            IdentityType identityType,
            Long ownerId
    ) {
        return sessions.findByIdForUpdate(id)
                .filter(session -> session.isOwnedBy(identityType, ownerId))
                .orElseThrow(() -> new AuthenticatedSessionLockedException("Session is unavailable"));
    }
}
