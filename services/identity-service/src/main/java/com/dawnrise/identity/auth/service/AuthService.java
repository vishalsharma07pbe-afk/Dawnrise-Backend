package com.dawnrise.identity.auth.service;

import com.dawnrise.identity.auth.dto.LoginRequest;
import com.dawnrise.identity.auth.dto.ChangePasswordRequest;
import com.dawnrise.identity.auth.model.AuthenticationResult;
import java.util.UUID;

public interface AuthService {

    AuthenticationResult login(
            LoginRequest request
    );

    AuthenticationResult refresh(
            String rawRefreshToken
    );

    AuthenticationResult unlockSession(String rawRefreshToken, String password);

    default AuthenticationResult unlockParent(String rawRefreshToken, String password) {
        return unlockSession(rawRefreshToken, password);
    }

    void logout(
            String rawRefreshToken
    );

    void changePassword(
            Long userId,
            ChangePasswordRequest request
    );
}
