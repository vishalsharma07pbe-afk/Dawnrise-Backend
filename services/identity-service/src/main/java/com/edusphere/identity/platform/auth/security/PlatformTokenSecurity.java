package com.edusphere.identity.platform.auth.security;

import com.edusphere.identity.auth.model.IdentityType;
import com.edusphere.identity.platform.auth.config.PlatformJwtProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("platformTokenSecurity")
public class PlatformTokenSecurity {

    private final PlatformJwtProperties jwtProperties;

    public PlatformTokenSecurity(PlatformJwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public boolean isPlatformUser(Authentication authentication) {
        try {
            if (!(authentication instanceof JwtAuthenticationToken token)) {
                return false;
            }

            String identityType =
                    token.getToken().getClaimAsString("identityType");

            return IdentityType.PLATFORM_USER.name().equals(identityType)
                    && token.getToken().getAudience() != null
                    && token.getToken()
                        .getAudience()
                        .contains(jwtProperties.getAudience());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
