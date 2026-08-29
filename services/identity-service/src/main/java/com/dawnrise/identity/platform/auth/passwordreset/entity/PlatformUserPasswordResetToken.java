package com.dawnrise.identity.platform.auth.passwordreset.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_user_password_reset_tokens")
public class PlatformUserPasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    @Column(name = "platform_user_id", nullable = false)
    private Long platformUserId;
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
    @Column(name = "used_at") private OffsetDateTime usedAt;
    @Column(name = "revoked_at") private OffsetDateTime revokedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PlatformUserPasswordResetToken() {}
    public PlatformUserPasswordResetToken(Long platformUserId, String tokenHash, OffsetDateTime expiresAt) {
        this.platformUserId = platformUserId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
    public Long getPlatformUserId() { return platformUserId; }
    public boolean isValidAt(OffsetDateTime now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }
    public void revoke(OffsetDateTime now) { if (usedAt == null && revokedAt == null) revokedAt = now; }
    public void markUsed(OffsetDateTime now) { if (usedAt != null) throw new IllegalStateException("Token already used"); usedAt = now; }
}
