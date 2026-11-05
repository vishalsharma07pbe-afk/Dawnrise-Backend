package com.dawnrise.identity.permission.repository;

import com.dawnrise.identity.permission.entity.Permission;
import com.dawnrise.identity.permission.entity.RolePermission;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class LeavePermissionLoadingTest {
    @Autowired private PermissionRepository permissions;
    @Autowired private RolePermissionRepository rolePermissions;

    @Test
    void rolePermissionQueryHydratesStaffStudentAndGuardianCodes() {
        grant(UserRole.TEACHER, PermissionCode.STAFF_LEAVE_SELF);
        grant(UserRole.TEACHER, PermissionCode.STAFF_LEAVE_CALENDAR_VIEW);
        grant(UserRole.STUDENT, PermissionCode.STUDENT_LEAVE_SELF);
        grant(UserRole.PARENT, PermissionCode.STUDENT_LEAVE_GUARDIAN);

        assertEquals(Set.of(PermissionCode.STAFF_LEAVE_SELF, PermissionCode.STAFF_LEAVE_CALENDAR_VIEW),
                rolePermissions.findActivePermissionCodesByRoles(Set.of(UserRole.TEACHER)));
        assertEquals(Set.of(PermissionCode.STUDENT_LEAVE_SELF, PermissionCode.STUDENT_LEAVE_GUARDIAN),
                rolePermissions.findActivePermissionCodesByRoles(Set.of(UserRole.STUDENT, UserRole.PARENT)));
    }

    private void grant(UserRole role, PermissionCode code) {
        Permission permission = permissions.saveAndFlush(new Permission(code, code.name(), "identity-service", false));
        rolePermissions.saveAndFlush(new RolePermission(role, permission));
    }
}
