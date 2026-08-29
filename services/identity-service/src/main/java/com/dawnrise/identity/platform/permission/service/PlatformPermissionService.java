package com.dawnrise.identity.platform.permission.service;

import com.dawnrise.identity.platform.permission.enums.PlatformPermissionCode;
import com.dawnrise.identity.platform.user.enums.PlatformRole;

import java.util.Set;

public interface PlatformPermissionService {

    Set<PlatformPermissionCode> getActivePermissionsForRoles(
            Set<PlatformRole> roles
    );
}
