package com.dawnrise.identity.platform.auth.dto;

import java.util.Set;

public record PlatformLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long platformUserId,
        String username,
        Set<String> roles
) {
}
