package com.dawnrise.identity.auth.refreshtoken.model;

import java.util.UUID;
public record RefreshTokenRotationResult(
        Long userId,
        String rawRefreshToken,
        UUID tokenFamilyId
) {
    public RefreshTokenRotationResult(Long userId, String rawRefreshToken) {
        this(userId, rawRefreshToken, UUID.randomUUID());
    }
}
