package com.edusphere.identity.platform.auth.security;

import com.edusphere.identity.auth.model.IdentityType;
import com.edusphere.identity.platform.permission.enums.PlatformPermissionCode;
import com.edusphere.identity.platform.user.enums.PlatformRole;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class PlatformAuthorizationContext {

    private final Long platformUserId;
    private final String username;
    private final Set<PlatformRole> roles;
    private final Set<PlatformPermissionCode> permissions;

    public PlatformAuthorizationContext(
            Long platformUserId,
            String username,
            Set<PlatformRole> roles,
            Set<PlatformPermissionCode> permissions
    ) {
        this.platformUserId = platformUserId;
        this.username = username;
        this.roles = roles == null
                ? Set.of()
                : Set.copyOf(roles);
        this.permissions = permissions == null
                ? Set.of()
                : Set.copyOf(permissions);
    }

    public static PlatformAuthorizationContext fromJwt(Jwt jwt) {
        String identityTypeClaim =
                jwt.getClaimAsString("identityType");

        if (!IdentityType.PLATFORM_USER.name()
                .equals(identityTypeClaim)) {
            throw new IllegalArgumentException(
                    "A platform user token is required"
            );
        }

        Collection<String> roleClaims =
                jwt.getClaimAsStringList("roles");

        Set<PlatformRole> roles = roleClaims == null
                ? Set.of()
                : roleClaims
                .stream()
                .map(PlatformRole::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        Collection<String> permissionClaims =
                jwt.getClaimAsStringList("permissions");

        Set<PlatformPermissionCode> permissions =
                permissionClaims == null
                        ? Set.of()
                        : permissionClaims
                        .stream()
                        .map(PlatformPermissionCode::valueOf)
                        .collect(Collectors.toUnmodifiableSet());

        return new PlatformAuthorizationContext(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                roles,
                permissions
        );
    }

    public Long getPlatformUserId() {
        return platformUserId;
    }

    public String getUsername() {
        return username;
    }

    public Set<PlatformRole> getRoles() {
        return roles;
    }

    public Set<PlatformPermissionCode> getPermissions() {
        return permissions;
    }

    public boolean hasRole(PlatformRole role) {
        return role != null && roles.contains(role);
    }

    public boolean hasPermission(
            PlatformPermissionCode permission
    ) {
        return permission != null
                && permissions.contains(permission);
    }
}