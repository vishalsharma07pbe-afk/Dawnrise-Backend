package com.dawnrise.identity.platform.auth.refreshtoken.service;

import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenRotationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenCreationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenSession;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface PlatformRefreshTokenService {

    String createRefreshToken(Long platformUserId);

    PlatformRefreshTokenCreationResult createRefreshTokenSession(Long platformUserId);

    PlatformRefreshTokenRotationResult rotateRefreshToken(
            String rawRefreshToken
    );

    PlatformRefreshTokenSession resolveRefreshTokenSession(String rawRefreshToken);

    void revokeTokenFamily(UUID tokenFamilyId, Long platformUserId, OffsetDateTime revokedAt);

    void revokeRefreshToken(String rawRefreshToken);

    void revokeAllForPlatformUser(Long platformUserId);
}
