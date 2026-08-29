package com.dawnrise.identity.auth.passwordreset.repository;

import com.dawnrise.identity.auth.passwordreset.entity.UserPasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserPasswordResetTokenRepository
        extends JpaRepository<UserPasswordResetToken, Long> {

    Optional<UserPasswordResetToken> findByTokenHash(String tokenHash);

    List<UserPasswordResetToken>
    findAllByUserIdAndUsedAtIsNullAndRevokedAtIsNull(Long userId);

    long countByUserIdAndCreatedAtAfter(
            Long userId,
            OffsetDateTime createdAfter
    );

    long countByUserIdAndUsedAtAfter(
            Long userId,
            OffsetDateTime usedAfter
    );
}
