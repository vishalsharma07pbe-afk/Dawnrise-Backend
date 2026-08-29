package com.dawnrise.identity.auth.model;

import com.dawnrise.identity.auth.dto.LoginResponse;

public record AuthenticationResult(
        LoginResponse response,
        String rawRefreshToken
) {
}