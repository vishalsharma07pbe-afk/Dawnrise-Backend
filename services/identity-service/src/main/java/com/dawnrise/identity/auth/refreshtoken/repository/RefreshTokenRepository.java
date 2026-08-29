package com.dawnrise.identity.auth.refreshtoken.repository;

import com.dawnrise.identity.auth.refreshtoken.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(
            String tokenHash
    );

    List<RefreshToken>
    findAllByTokenFamilyIdAndRevokedAtIsNull(
            UUID tokenFamilyId
    );

    List<RefreshToken>
    findAllByUserIdAndRevokedAtIsNull(
            Long userId
    );
}