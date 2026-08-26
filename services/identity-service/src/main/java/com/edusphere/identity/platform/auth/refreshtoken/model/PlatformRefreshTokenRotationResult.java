package com.edusphere.identity.platform.auth.refreshtoken.model;

public record PlatformRefreshTokenRotationResult(
        Long platformUserId,
        String rawRefreshToken
) {
}
