package com.dawnrise.identity.auth.security;

import com.dawnrise.identity.auth.model.IdentityType;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.platform.auth.config.PlatformJwtProperties;
import com.dawnrise.identity.platform.permission.enums.PlatformPermissionCode;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformRole;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class JwtServiceImpl implements JwtService {

    private static final String ISSUER =
            "dawnrise-identity-service";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final PlatformJwtProperties platformJwtProperties;

    public JwtServiceImpl(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            PlatformJwtProperties platformJwtProperties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.platformJwtProperties = platformJwtProperties;
    }

    @Override
    public String generateAccessToken(
            User user,
            Set<PermissionCode> permissions
    ) {
        Instant issuedAt = Instant.now();

        Instant expiresAt = issuedAt.plusSeconds(
                jwtProperties.getAccessTokenExpiration()
        );

        List<String> roles = user.getRoles()
                .stream()
                .map(UserRole::name)
                .sorted()
                .toList();

        List<String> permissionCodes = permissions
                .stream()
                .map(PermissionCode::name)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(
                        "organizationId",
                        user.getOrganizationId()
                )
                .claim(
                        "identityType",
                        IdentityType.ORGANIZATION_USER.name()
                )
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .claim("permissions", permissionCodes)
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    @Override
    public String generatePlatformAccessToken(
            PlatformUser platformUser,
            Set<PlatformPermissionCode> permissions
    ) {
        Instant issuedAt = Instant.now();

        Instant expiresAt = issuedAt.plusSeconds(
                platformJwtProperties.getAccessTokenExpiration()
        );

        List<String> roles = platformUser.getRoles()
                .stream()
                .map(PlatformRole::name)
                .sorted()
                .toList();

        List<String> permissionCodes = permissions
                .stream()
                .map(PlatformPermissionCode::name)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(platformJwtProperties.getAudience()))
                .subject(platformUser.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(
                        "identityType",
                        IdentityType.PLATFORM_USER.name()
                )
                .claim("username", platformUser.getUsername())
                .claim("roles", roles)
                .claim("permissions", permissionCodes)
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    @Override
    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpiration();
    }

    @Override
    public long getPlatformAccessTokenExpirationSeconds() {
        return platformJwtProperties.getAccessTokenExpiration();
    }
}
