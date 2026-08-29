package com.dawnrise.identity.auth.refreshtoken.policy;

import com.dawnrise.identity.auth.refreshtoken.config.RefreshTokenProperties;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenSessionLifetime;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenSessionLifetimePolicy {

    private final RefreshTokenProperties properties;

    public RefreshTokenSessionLifetimePolicy(
            RefreshTokenProperties properties
    ) {
        this.properties = properties;
    }

    public RefreshTokenSessionLifetime lifetimeFor(User user) {
        // Privileged roles get shorter sessions because they can access sensitive workflows.
        boolean hasShortSessionRole = user.getRoles()
                .stream()
                .anyMatch(properties.getShortSessionRoles()::contains);

        if (hasShortSessionRole) {
            return new RefreshTokenSessionLifetime(
                    properties.getShortSessionExpiration(),
                    properties.getShortSessionAbsoluteLifetime()
            );
        }

        // Standard users keep the longer classroom/family session window.
        return new RefreshTokenSessionLifetime(
                properties.getStandardSessionExpiration(),
                properties.getStandardSessionAbsoluteLifetime()
        );
    }
}
