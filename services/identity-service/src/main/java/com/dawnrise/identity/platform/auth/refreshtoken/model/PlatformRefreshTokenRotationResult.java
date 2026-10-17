package com.dawnrise.identity.platform.auth.refreshtoken.model;

import java.util.UUID;

public record PlatformRefreshTokenRotationResult(
        Long platformUserId,
        String rawRefreshToken,
        UUID tokenFamilyId
) {
    public PlatformRefreshTokenRotationResult(Long platformUserId, String rawRefreshToken) {
        this(platformUserId, rawRefreshToken, null);
    }
}
