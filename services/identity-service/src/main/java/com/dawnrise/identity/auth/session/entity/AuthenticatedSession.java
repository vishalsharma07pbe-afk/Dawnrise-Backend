package com.dawnrise.identity.auth.session.entity;

import com.dawnrise.identity.auth.model.IdentityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "authenticated_sessions")
public class AuthenticatedSession {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_type", nullable = false)
    private IdentityType identityType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "platform_user_id")
    private Long platformUserId;

    @Column(name = "authenticated_at", nullable = false)
    private OffsetDateTime authenticatedAt;

    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "retired_at")
    private OffsetDateTime retiredAt;

    @Version
    private Long version;

    protected AuthenticatedSession() {
    }

    private AuthenticatedSession(
            UUID id,
            IdentityType identityType,
            Long userId,
            Long platformUserId,
            OffsetDateTime authenticatedAt
    ) {
        this.id = id;
        this.identityType = identityType;
        this.userId = userId;
        this.platformUserId = platformUserId;
        this.authenticatedAt = authenticatedAt;
        this.lastActivityAt = authenticatedAt;
    }

    public static AuthenticatedSession organizationUser(
            UUID id,
            Long userId,
            OffsetDateTime authenticatedAt
    ) {
        return new AuthenticatedSession(
                id,
                IdentityType.ORGANIZATION_USER,
                userId,
                null,
                authenticatedAt
        );
    }

    public static AuthenticatedSession platformUser(
            UUID id,
            Long platformUserId,
            OffsetDateTime authenticatedAt
    ) {
        return new AuthenticatedSession(
                id,
                IdentityType.PLATFORM_USER,
                null,
                platformUserId,
                authenticatedAt
        );
    }

    public UUID getId() { return id; }
    public OffsetDateTime getAuthenticatedAt() { return authenticatedAt; }
    public OffsetDateTime getLastActivityAt() { return lastActivityAt; }
    public OffsetDateTime getRetiredAt() { return retiredAt; }
    public boolean isLocked() { return lockedAt != null || retiredAt != null; }

    public boolean isOwnedBy(IdentityType type, Long ownerId) {
        if (identityType != type) return false;
        return type == IdentityType.ORGANIZATION_USER
                ? ownerId.equals(userId)
                : ownerId.equals(platformUserId);
    }

    public void touch(OffsetDateTime now) { lastActivityAt = now; }
    public void lock(OffsetDateTime now) { if (lockedAt == null) lockedAt = now; }
    public void retire(OffsetDateTime now) { lock(now); if (retiredAt == null) retiredAt = now; }
}
