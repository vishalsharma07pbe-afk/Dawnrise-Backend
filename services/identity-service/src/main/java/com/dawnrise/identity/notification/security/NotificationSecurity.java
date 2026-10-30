package com.dawnrise.identity.notification.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("notificationSecurity")
public class NotificationSecurity {
    public boolean isOrganizationUser(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) return false;
        if (!"ORGANIZATION_USER".equals(token.getToken().getClaimAsString("identityType"))) return false;
        Object organization = token.getToken().getClaim("organizationId");
        if (!(organization instanceof Number number) || number.longValue() <= 0) return false;
        try {
            return Long.parseLong(token.getToken().getSubject()) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
