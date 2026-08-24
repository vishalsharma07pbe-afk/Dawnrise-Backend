package com.edusphere.identity.platform.auth.activation.repository;

import com.edusphere.identity.platform.auth.activation.entity.PlatformUserActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PlatformUserActivationTokenRepository
        extends JpaRepository<PlatformUserActivationToken, Long> {

    Optional<PlatformUserActivationToken> findByTokenHash(
            String tokenHash
    );

    List<PlatformUserActivationToken>
    findAllByPlatformUserIdAndUsedAtIsNullAndRevokedAtIsNull(
            Long platformUserId
    );

    long countByPlatformUserIdAndCreatedAtAfter(
            Long platformUserId,
            OffsetDateTime createdAfter
    );
}