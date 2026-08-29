package com.dawnrise.identity.platform.auth.passwordreset.repository;

import com.dawnrise.identity.platform.auth.passwordreset.entity.PlatformUserPasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PlatformUserPasswordResetTokenRepository extends JpaRepository<PlatformUserPasswordResetToken, Long> {
    Optional<PlatformUserPasswordResetToken> findByTokenHash(String tokenHash);
    List<PlatformUserPasswordResetToken> findAllByPlatformUserIdAndUsedAtIsNullAndRevokedAtIsNull(Long platformUserId);
    long countByPlatformUserIdAndCreatedAtAfter(Long platformUserId, OffsetDateTime createdAfter);
}
