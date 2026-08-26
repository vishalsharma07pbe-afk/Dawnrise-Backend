package com.edusphere.identity.platform.auth.model;

import com.edusphere.identity.platform.auth.dto.PlatformLoginResponse;

public record PlatformAuthenticationResult(
        PlatformLoginResponse response,
        String rawRefreshToken
) {
}
