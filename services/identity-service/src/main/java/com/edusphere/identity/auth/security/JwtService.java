package com.edusphere.identity.auth.security;

import com.edusphere.identity.permission.enums.PermissionCode;
import com.edusphere.identity.platform.permission.enums.PlatformPermissionCode;
import com.edusphere.identity.platform.user.entity.PlatformUser;
import com.edusphere.identity.user.entity.User;

import java.util.Set;

public interface JwtService {

    String generateAccessToken(
            User user,
            Set<PermissionCode> permissions
    );

    String generatePlatformAccessToken(
            PlatformUser platformUser,
            Set<PlatformPermissionCode> permissions
    );

    long getAccessTokenExpirationSeconds();

    long getPlatformAccessTokenExpirationSeconds();
}
