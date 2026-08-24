package com.edusphere.identity.platform.auth.refreshtoken.service;

import com.edusphere.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenRotationResult;

public interface PlatformRefreshTokenService {

    String createRefreshToken(Long platformUserId);

    PlatformRefreshTokenRotationResult rotateRefreshToken(
            String rawRefreshToken
    );

    void revokeRefreshToken(String rawRefreshToken);

    void revokeAllForPlatformUser(Long platformUserId);
}
