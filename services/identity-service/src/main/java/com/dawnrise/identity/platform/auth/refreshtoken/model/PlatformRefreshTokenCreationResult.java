package com.dawnrise.identity.platform.auth.refreshtoken.model;

import java.util.UUID;

public record PlatformRefreshTokenCreationResult(
        String rawRefreshToken,
        UUID tokenFamilyId
) {
}
