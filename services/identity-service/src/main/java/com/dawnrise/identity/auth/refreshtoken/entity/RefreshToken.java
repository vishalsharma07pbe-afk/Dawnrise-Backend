package com.dawnrise.identity.auth.refreshtoken.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refresh_tokens_token_hash",
                        columnNames = "token_hash"
                )
        }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(
            name = "token_family_id",
            nullable = false
    )
    private UUID tokenFamilyId;

    @Column(
            name = "token_hash",
            nullable = false,
            length = 64
    )
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "family_expires_at", nullable = false)
    private OffsetDateTime familyExpiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(
            name = "replaced_by_token_hash",
            length = 64
    )
    private String replacedByTokenHash;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    public RefreshToken() {
    }

    public RefreshToken(
            Long userId,
            UUID tokenFamilyId,
            String tokenHash,
            OffsetDateTime expiresAt,
            OffsetDateTime familyExpiresAt
    ) {
        this.userId = userId;
        this.tokenFamilyId = tokenFamilyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.familyExpiresAt = familyExpiresAt;
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

    public UUID getTokenFamilyId() {
        return tokenFamilyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getFamilyExpiresAt() {
        return familyExpiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public String getReplacedByTokenHash() {
        return replacedByTokenHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(OffsetDateTime currentTime) {
        return !expiresAt.isAfter(currentTime);
    }

    public boolean isFamilyExpired(OffsetDateTime currentTime) {
        return !familyExpiresAt.isAfter(currentTime);
    }

    public boolean isValidAt(OffsetDateTime currentTime) {
        return !isRevoked() && !isExpired(currentTime);
    }

    public void rotate(
            OffsetDateTime revokedAt,
            String replacementTokenHash
    ) {
        if (isRevoked()) {
            throw new IllegalStateException(
                    "Refresh token has already been revoked"
            );
        }

        this.revokedAt = revokedAt;
        this.replacedByTokenHash = replacementTokenHash;
    }

    public void revoke(OffsetDateTime revokedAt) {
        if (isRevoked()) {
            return;
        }

        this.revokedAt = revokedAt;
    }
}
