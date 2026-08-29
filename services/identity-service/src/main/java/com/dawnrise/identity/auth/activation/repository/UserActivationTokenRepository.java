package com.dawnrise.identity.auth.activation.repository;

import com.dawnrise.identity.auth.activation.entity.UserActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;

import java.util.List;
import java.util.Optional;

public interface UserActivationTokenRepository
        extends JpaRepository<UserActivationToken, Long> {

    Optional<UserActivationToken> findByTokenHash(
            String tokenHash
    );

    List<UserActivationToken>
    findAllByUserIdAndUsedAtIsNullAndRevokedAtIsNull(
            Long userId
    );

    long countByUserIdAndCreatedAtAfter(
            Long userId,
            OffsetDateTime createdAfter
    );
}