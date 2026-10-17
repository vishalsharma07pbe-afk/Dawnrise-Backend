package com.dawnrise.identity.auth.refreshtoken.service;

import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenRotationResult;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenCreationResult;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenSession;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface RefreshTokenService {

    String createRefreshToken(
            Long userId
    );

    RefreshTokenCreationResult createRefreshTokenSession(Long userId);

    RefreshTokenRotationResult rotateRefreshToken(
            String rawRefreshToken
    );

    RefreshTokenSession resolveRefreshTokenSession(String rawRefreshToken);

    void revokeRefreshToken(
            String rawRefreshToken
    );

    void revokeAllForUser(
            Long userId
    );

    void revokeTokenFamily(UUID tokenFamilyId, Long userId, OffsetDateTime revokedAt);
}
