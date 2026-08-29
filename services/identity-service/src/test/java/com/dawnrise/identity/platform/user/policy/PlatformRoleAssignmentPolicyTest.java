package com.dawnrise.identity.platform.user.policy;

import com.dawnrise.identity.platform.auth.security.PlatformAuthorizationContext;
import com.dawnrise.identity.platform.permission.enums.PlatformPermissionCode;
import com.dawnrise.identity.platform.user.enums.PlatformRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlatformRoleAssignmentPolicyTest {

    private final PlatformRoleAssignmentPolicy policy =
            new PlatformRoleAssignmentPolicy();

    @Test
    void identityAdminCannotAssignRestrictedRoles() {
        PlatformAuthorizationContext context = context(
                PlatformRole.PLATFORM_IDENTITY_ADMIN
        );

        assertThrows(
                AccessDeniedException.class,
                () -> policy.validateRoleAssignment(
                        context,
                        Set.of(PlatformRole.PLATFORM_SUPER_ADMIN)
                )
        );
    }

    @Test
    void superAdminCanAssignRestrictedRoles() {
        PlatformAuthorizationContext context = context(
                PlatformRole.PLATFORM_SUPER_ADMIN
        );

        assertDoesNotThrow(() -> policy.validateRoleAssignment(
                context,
                Set.of(PlatformRole.PLATFORM_IDENTITY_ADMIN)
        ));
    }

    @Test
    void ordinaryRolesAreAllowedForIdentityAdmin() {
        PlatformAuthorizationContext context = context(
                PlatformRole.PLATFORM_IDENTITY_ADMIN
        );

        assertDoesNotThrow(() -> policy.validateRoleAssignment(
                context,
                Set.of(PlatformRole.SALES)
        ));
    }

    private static PlatformAuthorizationContext context(
            PlatformRole role
    ) {
        return new PlatformAuthorizationContext(
                1L,
                "actor",
                Set.of(role),
                Set.of(PlatformPermissionCode.PLATFORM_ROLE_ASSIGN)
        );
    }
}
