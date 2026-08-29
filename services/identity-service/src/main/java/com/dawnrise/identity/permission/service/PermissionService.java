package com.dawnrise.identity.permission.service;

import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.user.enums.UserRole;

import java.util.Set;

public interface PermissionService {

    Set<PermissionCode> getActivePermissionsForRoles(
            Set<UserRole> roles
    );
}