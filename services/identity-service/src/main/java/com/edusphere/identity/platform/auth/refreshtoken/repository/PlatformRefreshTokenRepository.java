package com.edusphere.identity.platform.auth.refreshtoken.repository;

import com.edusphere.identity.platform.auth.refreshtoken.entity.PlatformRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformRefreshTokenRepository
        extends JpaRepository<PlatformRefreshToken, Long> {

    Optional<PlatformRefreshToken> findByTokenHash(String tokenHash);

    List<PlatformRefreshToken>
    findAllByTokenFamilyIdAndRevokedAtIsNull(UUID tokenFamilyId);

    List<PlatformRefreshToken>
    findAllByPlatformUserIdAndRevokedAtIsNull(Long platformUserId);
}
