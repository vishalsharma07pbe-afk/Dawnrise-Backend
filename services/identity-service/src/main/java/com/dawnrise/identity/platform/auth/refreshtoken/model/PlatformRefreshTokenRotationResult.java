package com.dawnrise.identity.platform.auth.refreshtoken.model;

public record PlatformRefreshTokenRotationResult(
        Long platformUserId,
        String rawRefreshToken
) {
}
