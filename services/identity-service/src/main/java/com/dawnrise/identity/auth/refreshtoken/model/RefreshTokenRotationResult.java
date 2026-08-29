package com.dawnrise.identity.auth.refreshtoken.model;

public record RefreshTokenRotationResult(
        Long userId,
        String rawRefreshToken
) {
}