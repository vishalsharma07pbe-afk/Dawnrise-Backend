package com.edusphere.identity.platform.permission.repository;

import com.edusphere.identity.platform.permission.entity.PlatformRolePermission;
import com.edusphere.identity.platform.permission.enums.PlatformPermissionCode;
import com.edusphere.identity.platform.user.enums.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface PlatformRolePermissionRepository
        extends JpaRepository<PlatformRolePermission, Long> {

    @Query("""
            SELECT DISTINCT rolePermission.permission.code
            FROM PlatformRolePermission rolePermission
            WHERE rolePermission.role IN :roles
              AND rolePermission.permission.active = true
            """)
    Set<PlatformPermissionCode> findActivePermissionCodesByRoles(
            @Param("roles") Set<PlatformRole> roles
    );
}
