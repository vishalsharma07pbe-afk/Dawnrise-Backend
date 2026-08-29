package com.dawnrise.identity.permission.service;

import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.permission.repository.RolePermissionRepository;
import com.dawnrise.identity.user.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PermissionServiceImplTest {

    private final RolePermissionRepository repository =
            mock(RolePermissionRepository.class);

    private final PermissionServiceImpl service =
            new PermissionServiceImpl(repository);

    @Test
    void getActivePermissionsForRoles_whenRolesNull_returnsEmptySet() {
        assertEquals(
                Set.of(),
                service.getActivePermissionsForRoles(null)
        );

        verifyNoInteractions(repository);
    }

    @Test
    void getActivePermissionsForRoles_whenRolesEmpty_returnsEmptySet() {
        assertEquals(
                Set.of(),
                service.getActivePermissionsForRoles(Set.of())
        );

        verifyNoInteractions(repository);
    }

    @Test
    void getActivePermissionsForRoles_whenOneRole_returnsRepositoryResult() {
        when(repository.findActivePermissionCodesByRoles(
                Set.of(UserRole.TEACHER)
        )).thenReturn(Set.of(PermissionCode.PROFILE_VIEW_SELF));

        assertEquals(
                Set.of(PermissionCode.PROFILE_VIEW_SELF),
                service.getActivePermissionsForRoles(Set.of(UserRole.TEACHER))
        );
    }

    @Test
    void getActivePermissionsForRoles_whenMultipleRoles_returnsDistinctPermissions() {
        Set<UserRole> roles =
                Set.of(UserRole.ADMIN, UserRole.HR);

        when(repository.findActivePermissionCodesByRoles(roles))
                .thenReturn(Set.of(
                        PermissionCode.USER_CREATE,
                        PermissionCode.USER_VIEW
                ));

        assertEquals(
                Set.of(
                        PermissionCode.USER_CREATE,
                        PermissionCode.USER_VIEW
                ),
                service.getActivePermissionsForRoles(roles)
        );
    }
}
