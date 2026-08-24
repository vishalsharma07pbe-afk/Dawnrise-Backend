package com.edusphere.identity.platform.permission.service;

import com.edusphere.identity.platform.permission.enums.PlatformPermissionCode;
import com.edusphere.identity.platform.user.enums.PlatformRole;

import java.util.Set;

public interface PlatformPermissionService {

    Set<PlatformPermissionCode> getActivePermissionsForRoles(
            Set<PlatformRole> roles
    );
}
