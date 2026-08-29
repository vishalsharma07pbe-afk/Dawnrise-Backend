package com.dawnrise.identity.permission.repository;

import com.dawnrise.identity.permission.entity.RolePermission;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RolePermissionRepository
        extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findAllByRole(UserRole role);

    Optional<RolePermission> findByRoleAndPermission_Code(
            UserRole role,
            PermissionCode permissionCode
    );

    boolean existsByRoleAndPermission_Code(
            UserRole role,
            PermissionCode permissionCode
    );

    @Modifying
    void deleteByRoleAndPermission_Code(
            UserRole role,
            PermissionCode permissionCode
    );

    @Query("""
            SELECT DISTINCT rp.permission.code
            FROM RolePermission rp
            WHERE rp.role IN :roles
              AND rp.permission.active = true
            """)
    Set<PermissionCode> findActivePermissionCodesByRoles(
            @Param("roles") Set<UserRole> roles
    );
}