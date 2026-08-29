package com.dawnrise.identity.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("tenantSecurity")
public class TenantSecurity {

    public boolean canAccessOrganization(
            Authentication authentication,
            Long requestedOrganizationId
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }

        if (requestedOrganizationId == null) {
            return false;
        }

        Object organizationClaim = jwtAuthentication
                .getToken()
                .getClaim("organizationId");

        if (!(organizationClaim instanceof Number tokenOrganizationId)) {
            return false;
        }

        return tokenOrganizationId.longValue()
                == requestedOrganizationId.longValue();
    }

    public boolean isCurrentUser(
            Authentication authentication,
            Long requestedUserId
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }

        if (requestedUserId == null) {
            return false;
        }

        String subject = jwtAuthentication
                .getToken()
                .getSubject();

        if (subject == null) {
            return false;
        }

        try {
            return Long.parseLong(subject)
                    == requestedUserId.longValue();
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}