package com.dawnrise.identity.user.controller;

import com.dawnrise.identity.auth.security.TenantSecurity;
import com.dawnrise.identity.auth.security.OrganizationTokenSecurity;
import com.dawnrise.identity.auth.security.UserAuthorization;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.config.SecurityConfig;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.user.dto.UserResponse;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.policy.UserStatusAuthorizationPolicy;
import com.dawnrise.identity.user.policy.UserStatusTransitionPolicy;
import com.dawnrise.identity.user.repository.UserRepository;
import com.dawnrise.identity.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        OrganizationTokenSecurity.class,
        TenantSecurity.class,
        UserAuthorization.class,
        UserStatusAuthorizationPolicy.class,
        UserStatusTransitionPolicy.class,
        GlobalExceptionHandler.class
})
class UserControllerSecurityIntegrationTest {

    private static final String BASE_URL =
            "/api/v1/organizations/{organizationId}/users";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void protectedEndpoint_whenUnauthenticated_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(get(BASE_URL + "/{userId}", 1L, 10L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_whenJwtInvalid_returnsUnauthorized()
            throws Exception {
        when(jwtDecoder.decode("bad-token"))
                .thenThrow(new BadJwtException("invalid"));

        mockMvc.perform(get(BASE_URL + "/{userId}", 1L, 10L)
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUser_withUserCreateAndAllowedHrRole_succeeds()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of("USER_CREATE"));
        when(userService.createUser(eq(1L), any(), any()))
                .thenReturn(response(10L, 1L, "teacher01"));

        mockMvc.perform(post(BASE_URL, 1L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody("TEACHER")))
                .andExpect(status().isCreated());

        verify(userService).createUser(eq(1L), any(), any());
    }

    @Test
    void createUser_missingUserCreate_returnsForbidden()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(post(BASE_URL, 1L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody("TEACHER")))
                .andExpect(status().isForbidden());

        verify(userService, never()).createUser(any(), any(), any());
    }

    @Test
    void organizationEndpoint_whenPlatformToken_returnsForbidden()
            throws Exception {
        when(jwtDecoder.decode("platform-token"))
                .thenReturn(Jwt.withTokenValue("platform-token")
                        .header("alg", "none")
                        .subject("99")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("identityType", "PLATFORM_USER")
                        .claim("organizationId", 1L)
                        .claim("roles", List.of("PLATFORM_SUPER_ADMIN"))
                        .claim("permissions", List.of("USER_VIEW"))
                        .build());

        mockMvc.perform(get(BASE_URL + "/{userId}", 1L, 10L)
                        .header(
                                "Authorization",
                                "Bearer platform-token"
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_userCreateDoesNotBypassBusinessRolePolicy()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of("USER_CREATE"));

        mockMvc.perform(post(BASE_URL, 1L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody("STUDENT")))
                .andExpect(status().isForbidden());

        verify(userService, never()).createUser(any(), any(), any());
    }

    @Test
    void viewSelf_withProfileViewSelf_succeeds() throws Exception {
        useJwt("token", 1L, 10L, Set.of("TEACHER"), Set.of("PROFILE_VIEW_SELF"));
        when(userService.getUserById(1L, 10L))
                .thenReturn(response(10L, 1L, "teacher01"));

        mockMvc.perform(get(BASE_URL + "/{userId}", 1L, 10L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void viewOther_withOnlyProfileViewSelf_returnsForbidden()
            throws Exception {
        useJwt("token", 1L, 10L, Set.of("TEACHER"), Set.of("PROFILE_VIEW_SELF"));

        mockMvc.perform(get(BASE_URL + "/{userId}", 1L, 11L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewOther_withUserViewSameTenant_succeeds() throws Exception {
        useJwt("token", 1L, 10L, Set.of("HR"), Set.of("USER_VIEW"));
        when(userService.getUserById(1L, 11L))
                .thenReturn(response(11L, 1L, "student01"));

        mockMvc.perform(get(BASE_URL + "/{userId}", 1L, 11L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void viewOther_withUserViewDifferentTenant_returnsForbidden()
            throws Exception {
        useJwt("token", 1L, 10L, Set.of("HR"), Set.of("USER_VIEW"));

        mockMvc.perform(get(BASE_URL + "/{userId}", 2L, 11L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSelf_withProfileUpdateSelf_succeeds() throws Exception {
        useJwt("token", 1L, 10L, Set.of("TEACHER"), Set.of("PROFILE_UPDATE_SELF"));
        when(userService.updateUserProfile(eq(1L), eq(10L), any()))
                .thenReturn(response(10L, 1L, "teacher01"));

        mockMvc.perform(put(BASE_URL + "/{userId}/profile", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody()))
                .andExpect(status().isOk());
    }

    @Test
    void updateOther_withOnlyProfileUpdateSelf_returnsForbidden()
            throws Exception {
        useJwt("token", 1L, 10L, Set.of("TEACHER"), Set.of("PROFILE_UPDATE_SELF"));

        mockMvc.perform(put(BASE_URL + "/{userId}/profile", 1L, 11L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateOther_withUserProfileUpdate_succeeds() throws Exception {
        useJwt("token", 1L, 10L, Set.of("HR"), Set.of("USER_PROFILE_UPDATE"));
        when(userService.updateUserProfile(eq(1L), eq(11L), any()))
                .thenReturn(response(11L, 1L, "student01"));

        mockMvc.perform(put(BASE_URL + "/{userId}/profile", 1L, 11L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody()))
                .andExpect(status().isOk());
    }

    @Test
    void resendActivation_withPermission_succeeds() throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of("USER_ACTIVATION_RESEND"));

        mockMvc.perform(post(BASE_URL + "/{userId}/activation/resend", 1L, 10L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isAccepted());

        verify(userService).resendActivationLink(eq(1L), eq(10L), any());
    }

    @Test
    void resendActivation_missingPermission_returnsForbidden()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(post(BASE_URL + "/{userId}/activation/resend", 1L, 10L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void routineRoleAssignment_withRoleAssignRoutine_succeeds()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of("ROLE_ASSIGN_ROUTINE"));
        when(userService.updateUserRoles(eq(1L), any(), eq(10L), any()))
                .thenReturn(response(10L, 1L, "teacher01"));

        mockMvc.perform(put(BASE_URL + "/{userId}/roles", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolesBody("TEACHER", "LIBRARIAN")))
                .andExpect(status().isOk());
    }

    @Test
    void routineRoleAssignment_withoutRoleAssignRoutine_returnsForbidden()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of());

        mockMvc.perform(put(BASE_URL + "/{userId}/roles", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolesBody("TEACHER", "LIBRARIAN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void routineRoleRemoval_withRoleRemoveRoutine_succeeds()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("HR"), Set.of("ROLE_REMOVE_ROUTINE"));
        when(userService.updateUserRoles(eq(1L), any(), eq(10L), any()))
                .thenReturn(response(10L, 1L, "teacher01"));

        mockMvc.perform(put(BASE_URL + "/{userId}/roles", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolesBody("TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    void sensitiveRoleRequest_withWorkflowPermission_reachesService()
            throws Exception {
        useJwt("token", 1L, 99L, Set.of("ADMIN"), Set.of(
                "ROLE_ASSIGNMENT_REQUEST_CREATE"
        ));
        when(userService.updateUserRoles(eq(1L), any(), eq(10L), any()))
                .thenReturn(response(10L, 1L, "teacher01"));

        mockMvc.perform(put(BASE_URL + "/{userId}/roles", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolesBody("TEACHER", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void statusActiveToSuspended_withUserSuspend_succeeds()
            throws Exception {
        statusAllowed("USER_SUSPEND", UserStatus.ACTIVE, UserStatus.SUSPENDED);
    }

    @Test
    void statusActiveToSuspended_withoutUserSuspend_returnsForbidden()
            throws Exception {
        statusDenied(Set.of(), UserStatus.ACTIVE, UserStatus.SUSPENDED);
    }

    @Test
    void statusActiveToInactive_withUserDeactivate_succeeds()
            throws Exception {
        statusAllowed("USER_DEACTIVATE", UserStatus.ACTIVE, UserStatus.INACTIVE);
    }

    @Test
    void statusSuspendedToActive_withUserReactivate_succeeds()
            throws Exception {
        statusAllowed("USER_REACTIVATE", UserStatus.SUSPENDED, UserStatus.ACTIVE);
    }

    @Test
    void statusSuspendedToActive_withWrongPermission_returnsForbidden()
            throws Exception {
        statusDenied(Set.of("USER_SUSPEND"), UserStatus.SUSPENDED, UserStatus.ACTIVE);
    }

    @Test
    void statusSelfUpdate_deniedByServiceDefense_returnsForbidden()
            throws Exception {
        useJwt("token", 1L, 10L, Set.of("ADMIN"), Set.of("USER_SUSPEND"));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user(10L, UserStatus.ACTIVE)));
        when(userService.updateUserStatus(eq(1L), any(), eq(10L), any()))
                .thenThrow(new ApprovalNotAllowedException(
                        "You cannot update your own account status"
                ));

        mockMvc.perform(put(BASE_URL + "/{userId}/status", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(UserStatus.SUSPENDED)))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusCrossTenant_returnsForbidden() throws Exception {
        useJwt("token", 1L, 99L, Set.of("ADMIN"), Set.of("USER_SUSPEND"));

        mockMvc.perform(put(BASE_URL + "/{userId}/status", 2L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(UserStatus.SUSPENDED)))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusInvalidTransitionDeniedBeforeService() throws Exception {
        useJwt("token", 1L, 99L, Set.of("ADMIN"), Set.of("USER_REACTIVATE"));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user(10L, UserStatus.PENDING_ACTIVATION)));

        mockMvc.perform(put(BASE_URL + "/{userId}/status", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
    }

    private void statusAllowed(
            String permission,
            UserStatus currentStatus,
            UserStatus requestedStatus
    ) throws Exception {
        useJwt("token", 1L, 99L, Set.of("ADMIN"), Set.of(permission));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user(10L, currentStatus)));
        when(userService.updateUserStatus(eq(1L), any(), eq(10L), any()))
                .thenReturn(response(10L, 1L, "teacher01"));

        mockMvc.perform(put(BASE_URL + "/{userId}/status", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(requestedStatus)))
                .andExpect(status().isOk());
    }

    private void statusDenied(
            Set<String> permissions,
            UserStatus currentStatus,
            UserStatus requestedStatus
    ) throws Exception {
        useJwt("token", 1L, 99L, Set.of("ADMIN"), permissions);
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(user(10L, currentStatus)));

        mockMvc.perform(put(BASE_URL + "/{userId}/status", 1L, 10L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(requestedStatus)))
                .andExpect(status().isForbidden());
    }

    private void useJwt(
            String token,
            Long organizationId,
            Long subject,
            Set<String> roles,
            Set<String> permissions
    ) {
        when(jwtDecoder.decode(token)).thenReturn(jwt(
                organizationId,
                subject,
                roles,
                permissions
        ));
    }

    private static Jwt jwt(
            Long organizationId,
            Long subject,
            Set<String> roles,
            Set<String> permissions
    ) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("organizationId", organizationId)
                .claim("roles", List.copyOf(roles))
                .claim("permissions", List.copyOf(permissions))
                .build();
    }

    private static User user(Long id, UserStatus status) {
        User user = new User(1L, "user" + id, "User", Set.of(UserRole.TEACHER));
        ReflectionTestUtils.setField(user, "id", id);
        user.setStatus(status);
        return user;
    }

    private static UserResponse response(
            Long id,
            Long organizationId,
            String username
    ) {
        UserResponse response = new UserResponse();
        response.setId(id);
        response.setOrganizationId(organizationId);
        response.setUsername(username);
        return response;
    }

    private static String createUserBody(String role) {
        return """
                {
                  "organizationId": 1,
                  "username": "teacher01",
                  "firstName": "Rahul",
                  "email": "teacher@dawnrise.com",
                  "roles": ["%s"]
                }
                """.formatted(role);
    }

    private static String profileBody() {
        return """
                {
                  "firstName": "Rahul",
                  "lastName": "Sharma",
                  "email": "teacher@dawnrise.com",
                  "phone": "+91 9876543210"
                }
                """;
    }

    private static String statusBody(UserStatus status) {
        return """
                {
                  "status": "%s"
                }
                """.formatted(status);
    }

    private static String rolesBody(String... roles) {
        return """
                {
                  "roles": [%s]
                }
                """.formatted(
                String.join(
                        ", ",
                        java.util.Arrays.stream(roles)
                                .map(role -> "\"" + role + "\"")
                                .toList()
                )
        );
    }
}
