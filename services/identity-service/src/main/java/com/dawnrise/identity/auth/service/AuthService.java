package com.dawnrise.identity.auth.service;

import com.dawnrise.identity.auth.dto.LoginRequest;
import com.dawnrise.identity.auth.dto.ChangePasswordRequest;
import com.dawnrise.identity.auth.model.AuthenticationResult;

public interface AuthService {

    AuthenticationResult login(
            LoginRequest request
    );

    AuthenticationResult refresh(
            String rawRefreshToken
    );

    void logout(
            String rawRefreshToken
    );

    void changePassword(
            Long userId,
            ChangePasswordRequest request
    );
}
