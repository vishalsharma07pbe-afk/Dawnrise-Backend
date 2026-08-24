package com.edusphere.identity.platform.auth.service;

import com.edusphere.identity.platform.auth.dto.PlatformLoginRequest;
import com.edusphere.identity.platform.auth.model.PlatformAuthenticationResult;

public interface PlatformAuthService {

    PlatformAuthenticationResult login(PlatformLoginRequest request);

    PlatformAuthenticationResult refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);
}
