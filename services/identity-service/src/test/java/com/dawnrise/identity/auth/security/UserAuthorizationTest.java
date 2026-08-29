package com.dawnrise.identity.auth.security;

import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.policy.UserStatusAuthorizationPolicy;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class UserAuthorizationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatusAuthorizationPolicy statusAuthorizationPolicy;

    private UserAuthorization userAuthorization;

    @BeforeEach
    void setUp() {
        userAuthorization = new UserAuthorization(
                userRepository,
                statusAuthorizationPolicy
        );
    }

    @Test
    void canCreateUser_governingAuthorityCreatingAdmin_returnsTrue() {
        boolean allowed = userAuthorization.canCreateUser(
                governingAuthority(),
                Set.of(UserRole.ADMIN)
        );

        assertTrue(allowed);
    }

    @Test
    void canCreateUser_governingAuthorityCreatingPrincipal_returnsTrue() {
        boolean allowed = userAuthorization.canCreateUser(
                governingAuthority(),
                Set.of(UserRole.PRINCIPAL)
        );

        assertTrue(allowed);
    }

    @Test
    void canCreateUser_governingAuthorityCreatingAnotherAuthority_returnsTrue() {
        boolean allowed = userAuthorization.canCreateUser(
                governingAuthority(),
                Set.of(UserRole.GOVERNING_AUTHORITY)
        );

        assertTrue(allowed);
    }

    @Test
    void canCreateUser_governingAuthorityCreatingTeacher_returnsFalse() {
        boolean allowed = userAuthorization.canCreateUser(
                governingAuthority(),
                Set.of(UserRole.TEACHER)
        );

        assertFalse(allowed);
    }

    @Test
    void canCreateUser_governingAuthorityCreatingMixedRoles_returnsFalse() {
        boolean allowed = userAuthorization.canCreateUser(
                governingAuthority(),
                Set.of(
                        UserRole.ADMIN,
                        UserRole.TEACHER
                )
        );

        assertFalse(allowed);
    }

    private Authentication governingAuthority() {
        return new TestingAuthenticationToken(
                "governing-user",
                null,
                "ROLE_GOVERNING_AUTHORITY"
        );
    }
}