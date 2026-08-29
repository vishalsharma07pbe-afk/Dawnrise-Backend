package com.dawnrise.identity.auth.refreshtoken.policy;

import com.dawnrise.identity.auth.refreshtoken.config.RefreshTokenProperties;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenSessionLifetime;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefreshTokenSessionLifetimePolicyTest {

    private RefreshTokenSessionLifetimePolicy policy;

    @BeforeEach
    void setUp() {
        RefreshTokenProperties properties =
                new RefreshTokenProperties();
        properties.setShortSessionRoles(List.of(
                UserRole.ADMIN,
                UserRole.PRINCIPAL,
                UserRole.HR,
                UserRole.ACCOUNTANT
        ));
        properties.setShortSessionExpiration(Duration.ofDays(7));
        properties.setShortSessionAbsoluteLifetime(
                Duration.ofDays(30)
        );
        properties.setStandardSessionRoles(List.of(
                UserRole.TEACHER,
                UserRole.STUDENT,
                UserRole.PARENT
        ));
        properties.setStandardSessionExpiration(Duration.ofDays(30));
        properties.setStandardSessionAbsoluteLifetime(
                Duration.ofDays(90)
        );

        policy = new RefreshTokenSessionLifetimePolicy(properties);
    }

    @Test
    void lifetimeFor_whenPrivilegedRole_returnsShortSession() {
        RefreshTokenSessionLifetime lifetime =
                policy.lifetimeFor(user(Set.of(UserRole.ADMIN)));

        assertEquals(Duration.ofDays(7),
                lifetime.inactivityExpiration());
        assertEquals(Duration.ofDays(30),
                lifetime.absoluteSessionLifetime());
    }

    @Test
    void lifetimeFor_whenStandardRole_returnsStandardSession() {
        RefreshTokenSessionLifetime lifetime =
                policy.lifetimeFor(user(Set.of(UserRole.STUDENT)));

        assertEquals(Duration.ofDays(30),
                lifetime.inactivityExpiration());
        assertEquals(Duration.ofDays(90),
                lifetime.absoluteSessionLifetime());
    }

    @Test
    void lifetimeFor_whenMixedRoles_privilegedRoleWins() {
        RefreshTokenSessionLifetime lifetime =
                policy.lifetimeFor(user(Set.of(
                        UserRole.TEACHER,
                        UserRole.HR
                )));

        assertEquals(Duration.ofDays(7),
                lifetime.inactivityExpiration());
        assertEquals(Duration.ofDays(30),
                lifetime.absoluteSessionLifetime());
    }

    @Test
    void lifetimeFor_whenOtherRole_usesStandardSession() {
        RefreshTokenSessionLifetime lifetime =
                policy.lifetimeFor(user(Set.of(UserRole.LIBRARIAN)));

        assertEquals(Duration.ofDays(30),
                lifetime.inactivityExpiration());
        assertEquals(Duration.ofDays(90),
                lifetime.absoluteSessionLifetime());
    }

    private static User user(Set<UserRole> roles) {
        return new User(
                1L,
                "user01",
                "Rahul",
                roles
        );
    }
}
