package com.edusphere.identity.auth.security;

import com.edusphere.identity.auth.model.IdentityType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("organizationTokenSecurity")
public class OrganizationTokenSecurity {

    public boolean isOrganizationUser(Authentication authentication) {
        try {
            if (!(authentication instanceof JwtAuthenticationToken token)) {
                return false;
            }

            String identityType =
                    token.getToken().getClaimAsString("identityType");

            return identityType == null
                    || IdentityType.ORGANIZATION_USER.name()
                    .equals(identityType);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
