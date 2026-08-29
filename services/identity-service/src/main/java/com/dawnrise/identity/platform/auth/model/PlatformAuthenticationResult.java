package com.dawnrise.identity.platform.auth.model;

import com.dawnrise.identity.platform.auth.dto.PlatformLoginResponse;

public record PlatformAuthenticationResult(
        PlatformLoginResponse response,
        String rawRefreshToken
) {
}
