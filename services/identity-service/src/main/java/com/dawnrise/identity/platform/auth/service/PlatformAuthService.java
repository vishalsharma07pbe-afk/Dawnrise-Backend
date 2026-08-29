package com.dawnrise.identity.platform.auth.service;

import com.dawnrise.identity.platform.auth.dto.PlatformLoginRequest;
import com.dawnrise.identity.platform.auth.model.PlatformAuthenticationResult;

public interface PlatformAuthService {

    PlatformAuthenticationResult login(PlatformLoginRequest request);

    PlatformAuthenticationResult refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);
}
