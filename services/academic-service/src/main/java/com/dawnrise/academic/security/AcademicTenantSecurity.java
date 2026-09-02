package com.dawnrise.academic.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("academicTenantSecurity")
public class AcademicTenantSecurity {

    public boolean hasOrganization(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtToken)
                || !authentication.isAuthenticated()) {
            return false;
        }

        Number organizationId =
                jwtToken.getToken().getClaim("organizationId");

        return organizationId != null
                && organizationId.longValue() > 0;
    }
}