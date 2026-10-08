package com.dawnrise.identity.auth.refreshtoken.service;

import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenRotationResult;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenCreationResult;

public interface RefreshTokenService {

    String createRefreshToken(
            Long userId
    );

    RefreshTokenCreationResult createRefreshTokenSession(Long userId);

    RefreshTokenRotationResult rotateRefreshToken(
            String rawRefreshToken
    );

    void revokeRefreshToken(
            String rawRefreshToken
    );

    void revokeAllForUser(
            Long userId
    );
}
