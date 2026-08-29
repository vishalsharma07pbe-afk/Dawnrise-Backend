package com.dawnrise.identity.auth.security;

import com.dawnrise.identity.permission.enums.PermissionCode;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthorizationContext {

    private final Long userId;
    private final Set<PermissionCode> permissions;

    public AuthorizationContext(
            Long userId,
            Set<PermissionCode> permissions
    ) {
        this.userId = userId;
        this.permissions = permissions == null
                ? Set.of()
                : Set.copyOf(permissions);
    }

    public static AuthorizationContext fromJwt(Jwt jwt) {
        Collection<String> permissionClaims =
                jwt.getClaimAsStringList("permissions");

        Set<PermissionCode> permissions = permissionClaims == null
                ? Set.of()
                : permissionClaims
                .stream()
                .map(PermissionCode::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        return new AuthorizationContext(
                Long.valueOf(jwt.getSubject()),
                permissions
        );
    }

    public Long getUserId() {
        return userId;
    }

    public Set<PermissionCode> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(PermissionCode permission) {
        return permissions.contains(permission);
    }
}
