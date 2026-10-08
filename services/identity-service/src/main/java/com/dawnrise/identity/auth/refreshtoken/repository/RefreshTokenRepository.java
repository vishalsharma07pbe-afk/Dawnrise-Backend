package com.dawnrise.identity.auth.refreshtoken.repository;

import com.dawnrise.identity.auth.refreshtoken.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenFamilyId = :tokenFamilyId and token.revokedAt is null order by token.id")
    List<RefreshToken> findAllByTokenFamilyIdAndRevokedAtIsNull(
            @Param("tokenFamilyId") UUID tokenFamilyId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.userId = :userId and token.revokedAt is null order by token.id")
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(
            @Param("userId") Long userId
    );
}
