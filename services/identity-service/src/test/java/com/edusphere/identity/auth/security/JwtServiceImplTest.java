package com.edusphere.identity.auth.security;

import com.edusphere.identity.permission.enums.PermissionCode;
import com.edusphere.identity.platform.auth.config.PlatformJwtProperties;
import com.edusphere.identity.platform.permission.enums.PlatformPermissionCode;
import com.edusphere.identity.platform.user.entity.PlatformUser;
import com.edusphere.identity.platform.user.enums.PlatformRole;
import com.edusphere.identity.user.entity.User;
import com.edusphere.identity.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class JwtServiceImplTest {

    @Test
    void generateAccessToken_writesSortedRolesAndPermissionsClaims() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        JwtProperties properties = new JwtProperties();
        properties.setAccessTokenExpiration(900);
        PlatformJwtProperties platformProperties =
                new PlatformJwtProperties();

        when(encoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(Jwt.withTokenValue("jwt-token")
                        .header("alg", "RS256")
                        .issuer("edusphere-identity-service")
                        .subject("10")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(900))
                        .build());

        JwtServiceImpl service =
                new JwtServiceImpl(
                        encoder,
                        properties,
                        platformProperties
                );

        User user = new User(
                1L,
                "teacher01",
                "Rahul",
                Set.of(UserRole.HR, UserRole.TEACHER)
        );
        ReflectionTestUtils.setField(user, "id", 10L);

        String token = service.generateAccessToken(
                user,
                Set.of(
                        PermissionCode.USER_VIEW,
                        PermissionCode.PROFILE_VIEW_SELF
                )
        );

        assertEquals("jwt-token", token);

        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);

        verify(encoder).encode(captor.capture());

        assertEquals(
                List.of("HR", "TEACHER"),
                captor.getValue().getClaims().getClaim("roles")
        );
        assertEquals(
                List.of("PROFILE_VIEW_SELF", "USER_VIEW"),
                captor.getValue().getClaims().getClaim("permissions")
        );
    }

    @Test
    void generatePlatformAccessToken_writesPlatformClaimsAndAudience() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        JwtProperties properties = new JwtProperties();
        properties.setAccessTokenExpiration(900);
        PlatformJwtProperties platformProperties =
                new PlatformJwtProperties();
        platformProperties.setAudience("dawnrise-operations");
        platformProperties.setAccessTokenExpiration(600);

        when(encoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(Jwt.withTokenValue("platform-jwt-token")
                        .header("alg", "RS256")
                        .issuer("edusphere-identity-service")
                        .audience(List.of("dawnrise-operations"))
                        .subject("99")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .build());

        JwtServiceImpl service =
                new JwtServiceImpl(
                        encoder,
                        properties,
                        platformProperties
                );

        PlatformUser platformUser = new PlatformUser(
                "employee01",
                "Asha",
                "asha@dawnrise.in",
                Set.of(
                        PlatformRole.SALES,
                        PlatformRole.ONBOARDING_SPECIALIST
                )
        );
        ReflectionTestUtils.setField(platformUser, "id", 99L);

        String token = service.generatePlatformAccessToken(
                platformUser,
                Set.of(
                        PlatformPermissionCode.LEAD_VIEW,
                        PlatformPermissionCode.PLATFORM_PROFILE_VIEW_SELF
                )
        );

        assertEquals("platform-jwt-token", token);

        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);

        verify(encoder).encode(captor.capture());

        assertEquals(
                List.of("dawnrise-operations"),
                captor.getValue().getClaims().getClaims().get("aud")
        );
        assertEquals(
                "PLATFORM_USER",
                captor.getValue().getClaims().getClaim("identityType")
        );
        assertEquals(
                List.of("ONBOARDING_SPECIALIST", "SALES"),
                captor.getValue().getClaims().getClaim("roles")
        );
        assertEquals(
                List.of("LEAD_VIEW", "PLATFORM_PROFILE_VIEW_SELF"),
                captor.getValue().getClaims().getClaim("permissions")
        );
    }
}
