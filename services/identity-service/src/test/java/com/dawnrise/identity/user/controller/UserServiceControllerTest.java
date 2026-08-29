package com.dawnrise.identity.user.controller;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.user.dto.UserResponse;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.common.exception.GlobalExceptionHandler;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
public class UserServiceControllerTest {

    private static final String BASE_URL =
            "/api/v1/organizations/{organizationId}/users";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(jwtPrincipal("99"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUser_whenValid_returnsCreatedUser() throws Exception {
        UserResponse response = response(10L, 1L, "teacher01");

        when(userService.createUser(eq(1L), any(com.dawnrise.identity.auth.security.AuthorizationContext.class), any()))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": 1,
                                  "username": "teacher01",
                                  "firstName": "Rahul",
                                  "lastName": "Sharma",
                                  "email": "teacher@dawnrise.com",
                                  "phone": "+91 9876543210",
                                  "roles": ["TEACHER"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.organizationId").value(1))
                .andExpect(jsonPath("$.username").value("teacher01"))
                .andExpect(jsonPath("$.firstName").value("Rahul"))
                .andExpect(jsonPath("$.lastName").value("Sharma"))
                .andExpect(jsonPath("$.email").value("teacher@dawnrise.com"))
                .andExpect(jsonPath("$.phone").value("+91 9876543210"))
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(userService).createUser(eq(1L), any(com.dawnrise.identity.auth.security.AuthorizationContext.class), any());
    }

    @Test
    void createUser_whenOrganizationIdMismatch_returnsBadRequest()
            throws Exception {
        mockMvc.perform(post(BASE_URL, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": 2,
                                  "username": "teacher01",
                                  "firstName": "Rahul",
                                  "email": "teacher@dawnrise.com",
                                  "roles": ["TEACHER"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Organization ID in the URL must match "
                                + "the request body"))
                .andExpect(jsonPath("$.path").value(
                        "/api/v1/organizations/1/users"));

        verify(userService, never()).createUser(any(), any(), any());
    }

    @Test
    void createUser_whenRequestInvalid_returnsValidationErrors()
            throws Exception {
        mockMvc.perform(post(BASE_URL, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": 1,
                                  "username": "ab",
                                  "firstName": "",
                                  "email": "invalid-email",
                                  "roles": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.username").value(
                        "Username must be between 3 and 100 characters"))
                .andExpect(jsonPath("$.validationErrors.firstName").value(
                        "First name is required"))
                .andExpect(jsonPath("$.validationErrors.email").value(
                        "Email must be valid"))
                .andExpect(jsonPath("$.validationErrors.roles").value(
                        "At least one role is required"));

        verify(userService, never()).createUser(any(), any(), any());
    }

    @Test
    void createUser_whenDuplicateResource_returnsConflict() throws Exception {
        when(userService.createUser(eq(1L), any(com.dawnrise.identity.auth.security.AuthorizationContext.class), any()))
                .thenThrow(new DuplicateResourceException(
                        "Username already exists in this organization: "
                                + "teacher01"));

        mockMvc.perform(post(BASE_URL, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": 1,
                                  "username": "teacher01",
                                  "firstName": "Rahul",
                                  "email": "teacher@dawnrise.com",
                                  "roles": ["TEACHER"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Username already exists in this organization: "
                                + "teacher01"));
    }

    @Test
    void getUserById_whenFound_returnsUser() throws Exception {
        UserResponse response = response(10L, 1L, "teacher01");

        when(userService.getUserById(1L, 10L))
                .thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/{userId}", 1L, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.organizationId").value(1))
                .andExpect(jsonPath("$.username").value("teacher01"));

        verify(userService).getUserById(1L, 10L);
    }

    @Test
    void getUserById_whenMissing_returnsNotFound() throws Exception {
        when(userService.getUserById(1L, 10L))
                .thenThrow(new ResourceNotFoundException(
                        "User not found with ID: 10 in organization: 1"));

        mockMvc.perform(get(BASE_URL + "/{userId}", 1L, 10L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(
                        "User not found with ID: 10 in organization: 1"));
    }

    @Test
    void getAllUsers_whenUsersExist_returnsPageResponse() throws Exception {
        UserResponse firstUser = response(10L, 1L, "teacher01");
        UserResponse secondUser = response(11L, 1L, "student01");
        PageResponse<UserResponse> pageResponse = new PageResponse<>(
                List.of(firstUser, secondUser),
                1,
                2,
                5,
                3,
                false,
                false,
                false
        );

        when(userService.getAllUsersByOrganization(eq(1L), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_URL, 1L)
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "username,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[1].id").value(11))
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.empty").value(false));

        verify(userService)
                .getAllUsersByOrganization(eq(1L), any(Pageable.class));
    }

    @Test
    void updateUserProfile_whenValid_returnsUpdatedUser() throws Exception {
        UserResponse response = response(10L, 1L, "teacher01");
        response.setFirstName("Rohan");
        response.setEmail("rohan@dawnrise.com");
        response.setPhone("+91 9999999999");

        when(userService.updateUserProfile(eq(1L), eq(10L), any()))
                .thenReturn(response);

        mockMvc.perform(put(BASE_URL + "/{userId}/profile", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Rohan",
                                  "lastName": "Sharma",
                                  "email": "rohan@dawnrise.com",
                                  "phone": "+91 9999999999"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.firstName").value("Rohan"))
                .andExpect(jsonPath("$.email").value("rohan@dawnrise.com"))
                .andExpect(jsonPath("$.phone").value("+91 9999999999"));

        verify(userService).updateUserProfile(eq(1L), eq(10L), any());
    }

    @Test
    void updateUserProfile_whenInvalid_returnsValidationErrors()
            throws Exception {
        mockMvc.perform(put(BASE_URL + "/{userId}/profile", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "",
                                  "email": "invalid-email",
                                  "phone": "12"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.firstName").value(
                        "First name is required"))
                .andExpect(jsonPath("$.validationErrors.email").value(
                        "Email must be valid"))
                .andExpect(jsonPath("$.validationErrors.phone").value(
                        "Phone number must be valid"));

        verify(userService, never())
                .updateUserProfile(any(), any(), any());
    }

    @Test
    void updateUserRoles_whenValid_returnsUpdatedUser() throws Exception {
        UserResponse response = response(10L, 1L, "teacher01");
        response.setRoles(Set.of(UserRole.ADMIN, UserRole.TEACHER));

        when(userService.updateUserRoles(eq(1L), any(com.dawnrise.identity.auth.security.AuthorizationContext.class), eq(10L), any()))
                .thenReturn(response);

        mockMvc.perform(put(BASE_URL + "/{userId}/roles", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": ["ADMIN", "TEACHER"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.roles[*]",
                        containsInAnyOrder("ADMIN", "TEACHER")));

        verify(userService).updateUserRoles(eq(1L), any(com.dawnrise.identity.auth.security.AuthorizationContext.class), eq(10L), any());
    }

    @Test
    void updateUserRoles_whenInvalid_returnsValidationErrors()
            throws Exception {
        mockMvc.perform(put(BASE_URL + "/{userId}/roles", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.roles").value(
                        "At least one role is required"));

        verify(userService, never()).updateUserRoles(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void updateUserStatus_whenValid_returnsUpdatedUser() throws Exception {
        UserResponse response = response(10L, 1L, "teacher01");
        response.setStatus(UserStatus.SUSPENDED);

        when(userService.updateUserStatus(eq(1L), any(com.dawnrise.identity.auth.security.AuthorizationContext.class), eq(10L), any()))
                .thenReturn(response);

        mockMvc.perform(put(BASE_URL + "/{userId}/status", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(userService).updateUserStatus(
                eq(1L),
                any(com.dawnrise.identity.auth.security.AuthorizationContext.class),
                eq(10L),
                any()
        );
    }

    @Test
    void updateUserStatus_whenInvalid_returnsValidationErrors()
            throws Exception {
        mockMvc.perform(put(BASE_URL + "/{userId}/status", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.status").value(
                        "User status is required"));

        verify(userService, never()).updateUserStatus(
                any(),
                any(),
                any(),
                any()
        );
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
        response.setFirstName("Rahul");
        response.setLastName("Sharma");
        response.setEmail("teacher@dawnrise.com");
        response.setPhone("+91 9876543210");
        response.setRoles(Set.of(UserRole.TEACHER));
        response.setStatus(UserStatus.ACTIVE);
        return response;
    }

    private static JwtAuthenticationToken jwtPrincipal(String subject) {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", subject)
        );
        return new JwtAuthenticationToken(jwt);
    }
}
