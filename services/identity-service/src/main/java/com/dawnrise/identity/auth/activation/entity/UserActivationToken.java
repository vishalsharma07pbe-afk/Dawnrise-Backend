package com.dawnrise.identity.auth.activation.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "user_activation_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_activation_tokens_token_hash",
                        columnNames = "token_hash"
                )
        }
)
public class UserActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    public UserActivationToken() {
    }

    public UserActivationToken(
            Long userId,
            String tokenHash,
            OffsetDateTime expiresAt
    ) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Long getUserId() {
        return userId;
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

    public boolean isValidAt(OffsetDateTime currentTime) {
        return !isUsed()
                && !isRevoked()
                && !isExpired(currentTime);
    }

    public void revoke(OffsetDateTime revokedAt) {
        if (isUsed()) {
            throw new IllegalStateException(
                    "A used activation token cannot be revoked"
            );
        }

        if (isRevoked()) {
            return;
        }

        this.revokedAt = revokedAt;
    }

    public boolean isExpired(OffsetDateTime currentTime) {
        return !expiresAt.isAfter(currentTime);
    }

    public void markUsed(OffsetDateTime usedAt) {
        if (isUsed()) {
            throw new IllegalStateException(
                    "Activation token has already been used"
            );
        }

        this.usedAt = usedAt;
    }
}