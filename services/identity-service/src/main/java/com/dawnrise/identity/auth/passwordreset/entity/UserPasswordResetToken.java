package com.dawnrise.identity.auth.passwordreset.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "user_password_reset_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_password_reset_tokens_token_hash",
                        columnNames = "token_hash"
                )
        }
)
public class UserPasswordResetToken {

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

    public UserPasswordResetToken() {
    }

    public UserPasswordResetToken(
            Long userId,
            String tokenHash,
            OffsetDateTime expiresAt
    ) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isValidAt(OffsetDateTime currentTime) {
        return usedAt == null
                && revokedAt == null
                && expiresAt.isAfter(currentTime);
    }

    public void revoke(OffsetDateTime revokedAt) {
        if (usedAt != null) {
            throw new IllegalStateException(
                    "A used password reset token cannot be revoked"
            );
        }

        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }

    public void markUsed(OffsetDateTime usedAt) {
        if (this.usedAt != null) {
            throw new IllegalStateException(
                    "Password reset token has already been used"
            );
        }

        this.usedAt = usedAt;
    }
}
