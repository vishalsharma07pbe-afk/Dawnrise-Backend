package com.edusphere.identity.platform.user.policy;

import com.edusphere.identity.platform.auth.security.PlatformAuthorizationContext;
import com.edusphere.identity.platform.user.enums.PlatformRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PlatformRoleAssignmentPolicy {

    private static final Set<PlatformRole> RESTRICTED_ROLES =
            Set.of(
                    PlatformRole.PLATFORM_SUPER_ADMIN,
                    PlatformRole.PLATFORM_IDENTITY_ADMIN
            );

    public void validateRoleAssignment(
            PlatformAuthorizationContext authorizationContext,
            Set<PlatformRole> requestedRoles
    ) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one platform role is required"
            );
        }

        boolean containsRestrictedRole = requestedRoles
                .stream()
                .anyMatch(RESTRICTED_ROLES::contains);

        if (containsRestrictedRole
                && !authorizationContext.hasRole(
                PlatformRole.PLATFORM_SUPER_ADMIN
        )) {
            throw new AccessDeniedException(
                    "Only a platform super administrator can assign "
                            + "restricted platform roles"
            );
        }
    }

    public boolean isRestrictedRole(PlatformRole role) {
        return role != null && RESTRICTED_ROLES.contains(role);
    }
}