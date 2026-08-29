package com.dawnrise.identity.auth.security;

import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.platform.permission.enums.PlatformPermissionCode;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.user.entity.User;

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
