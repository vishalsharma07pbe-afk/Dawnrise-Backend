package com.edusphere.school.school.controller;

import com.edusphere.school.config.SecurityConfig;
import com.edusphere.school.school.DTO.SchoolProvisioningResponse;
import com.edusphere.school.school.enums.ProvisioningStatus;
import com.edusphere.school.school.enums.SchoolStatus;
import com.edusphere.school.school.exception.GlobalExceptionHandler;
import com.edusphere.school.school.service.SchoolService;
import com.edusphere.school.security.PlatformTokenSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchoolController.class)
@Import({
        SecurityConfig.class,
        PlatformTokenSecurity.class,
        GlobalExceptionHandler.class
})
class SchoolControllerSecurityIntegrationTest {

    private static final String BASE_URL = "/api/v1/schools";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SchoolService schoolService;

    @Test
    void schoolEndpoint_whenUnauthenticated_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(schoolService);
    }

    @Test
    void schoolEndpoint_whenJwtInvalid_returnsUnauthorized()
            throws Exception {
        when(jwtDecoder.decode("bad-token"))
                .thenThrow(new BadJwtException("invalid"));

        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(schoolService);
    }

    @Test
    void schoolEndpoint_whenOrganizationToken_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("organization-token"))
                .thenReturn(jwt(
                        "organization-token",
                        "ORGANIZATION_USER",
                        List.of("dawnrise-operations"),
                        Set.of("ORGANIZATION_VIEW")
                ));

        mockMvc.perform(get(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer organization-token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(schoolService);
    }

    @Test
    void schoolEndpoint_whenPlatformTokenWrongAudience_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("wrong-audience-token"))
                .thenReturn(jwt(
                        "wrong-audience-token",
                        "PLATFORM_USER",
                        List.of("school-management"),
                        Set.of("ORGANIZATION_VIEW")
                ));

        mockMvc.perform(get(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer wrong-audience-token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(schoolService);
    }

    @Test
    void listSchools_whenPlatformTokenMissingPermission_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("platform-token"))
                .thenReturn(jwt(
                        "platform-token",
                        "PLATFORM_USER",
                        List.of("dawnrise-operations"),
                        Set.of("ORGANIZATION_UPDATE")
                ));

        mockMvc.perform(get(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer platform-token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(schoolService);
    }

    @Test
    void createSchool_whenPlatformTokenMissingOneRequiredPermission_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("platform-token"))
                .thenReturn(jwt(
                        "platform-token",
                        "PLATFORM_USER",
                        List.of("dawnrise-operations"),
                        Set.of("ORGANIZATION_CREATE")
                ));

        mockMvc.perform(post(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer platform-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(schoolService);
    }

    @Test
    void createSchool_whenPlatformTokenHasPermissions_reachesService()
            throws Exception {
        when(jwtDecoder.decode("platform-token"))
                .thenReturn(jwt(
                        "platform-token",
                        "PLATFORM_USER",
                        List.of("dawnrise-operations"),
                        Set.of(
                                "ORGANIZATION_CREATE",
                                "PROVISIONING_START"
                        )
                ));

        when(schoolService.onboardSchool(any()))
                .thenReturn(new SchoolProvisioningResponse(
                        1L,
                        SchoolStatus.ACTIVE,
                        ProvisioningStatus.SUCCEEDED,
                        1,
                        null,
                        null,
                        null
                ));

        mockMvc.perform(post(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer platform-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isCreated());

        verify(schoolService).onboardSchool(any());
    }

    private static Jwt jwt(
            String tokenValue,
            String identityType,
            List<String> audience,
            Set<String> permissions
    ) {
        Jwt.Builder builder = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .issuer("edusphere-identity-service")
                .subject("42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("username", "employee01")
                .claim("roles", Set.of("ONBOARDING_MANAGER"))
                .claim("permissions", permissions);

        if (identityType != null) {
            builder.claim("identityType", identityType);
        }

        if (!audience.isEmpty()) {
            builder.audience(audience);
        }

        return builder.build();
    }

    private static String validCreateRequest() {
        return """
                {
                  "schoolCode": "SCH001",
                  "name": "EduSphere Public School",
                  "email": "school@edusphere.com",
                  "phone": "9876543210",
                  "address": "New Delhi",
                  "initialAuthority": {
                    "firstName": "Authority",
                    "lastName": "One",
                    "username": "authority.one",
                    "email": "authority@edusphere.com",
                    "phone": "9876543211"
                  }
                }
                """;
    }
}
