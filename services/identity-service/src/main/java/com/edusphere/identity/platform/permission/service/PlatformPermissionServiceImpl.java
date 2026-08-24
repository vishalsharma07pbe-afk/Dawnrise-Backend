package com.edusphere.identity.platform.permission.service;

import com.edusphere.identity.platform.permission.enums.PlatformPermissionCode;
import com.edusphere.identity.platform.permission.repository.PlatformRolePermissionRepository;
import com.edusphere.identity.platform.user.enums.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class PlatformPermissionServiceImpl
        implements PlatformPermissionService {

    private final PlatformRolePermissionRepository rolePermissionRepository;

    public PlatformPermissionServiceImpl(
            PlatformRolePermissionRepository rolePermissionRepository
    ) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PlatformPermissionCode> getActivePermissionsForRoles(
            Set<PlatformRole> roles
    ) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        return rolePermissionRepository
                .findActivePermissionCodesByRoles(roles);
    }
}
