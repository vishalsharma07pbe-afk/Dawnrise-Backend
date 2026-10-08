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

    AuthenticationResult unlockParent(Long userId, UUID sessionId, String password);

    void logout(
            String rawRefreshToken
    );

    void changePassword(
            Long userId,
            ChangePasswordRequest request
    );
}
