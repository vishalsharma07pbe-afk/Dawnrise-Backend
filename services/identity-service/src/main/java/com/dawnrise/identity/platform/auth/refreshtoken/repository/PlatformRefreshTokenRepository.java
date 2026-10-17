package com.dawnrise.identity.platform.auth.refreshtoken.repository;

import com.dawnrise.identity.platform.auth.refreshtoken.entity.PlatformRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformRefreshTokenRepository
        extends JpaRepository<PlatformRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PlatformRefreshToken token where token.tokenHash = :tokenHash")
    Optional<PlatformRefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PlatformRefreshToken token where token.tokenFamilyId = :tokenFamilyId and token.revokedAt is null order by token.id")
    List<PlatformRefreshToken> findAllByTokenFamilyIdAndRevokedAtIsNull(@Param("tokenFamilyId") UUID tokenFamilyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PlatformRefreshToken token where token.platformUserId = :platformUserId and token.revokedAt is null order by token.id")
    List<PlatformRefreshToken> findAllByPlatformUserIdAndRevokedAtIsNull(@Param("platformUserId") Long platformUserId);
}
