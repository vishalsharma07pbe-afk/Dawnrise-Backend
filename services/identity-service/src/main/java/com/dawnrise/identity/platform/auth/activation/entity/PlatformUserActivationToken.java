package com.dawnrise.identity.platform.auth.activation.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "platform_user_activation_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_platform_user_activation_tokens_hash",
                        columnNames = "token_hash"
                )
        }
)
public class PlatformUserActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "platform_user_id", nullable = false)
    private Long platformUserId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public PlatformUserActivationToken() {
    }

    public PlatformUserActivationToken(
            Long platformUserId,
            String tokenHash,
            OffsetDateTime expiresAt
    ) {
        this.platformUserId = platformUserId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Long getPlatformUserId() {
        return platformUserId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(OffsetDateTime currentTime) {
        return !expiresAt.isAfter(currentTime);
    }

    public boolean isValidAt(OffsetDateTime currentTime) {
        return !isUsed()
                && !isRevoked()
                && !isExpired(currentTime);
    }

    public void revoke(OffsetDateTime revokedAt) {
        if (isUsed()) {
            throw new IllegalStateException(
                    "A used platform activation token cannot be revoked"
            );
        }

        if (isRevoked()) {
            return;
        }

        this.revokedAt = revokedAt;
    }

    public void markUsed(OffsetDateTime usedAt) {
        if (isUsed()) {
            throw new IllegalStateException(
                    "Platform activation token has already been used"
            );
        }

        if (isRevoked()) {
            throw new IllegalStateException(
                    "A revoked platform activation token cannot be used"
            );
        }

        this.usedAt = usedAt;
    }
}