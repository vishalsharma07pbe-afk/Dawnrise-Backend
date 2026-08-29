package com.dawnrise.identity.auth.lockout;

import com.dawnrise.identity.auth.exception.AccountLockedException;
import com.dawnrise.identity.auth.exception.InvalidCredentialsException;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginLockoutServiceTest {

    private LoginLockoutService service;

    @BeforeEach
    void setUp() {
        LoginLockoutProperties properties =
                new LoginLockoutProperties();
        properties.setFirstFailureThreshold(2);
        properties.setEscalatedFailureThreshold(2);
        properties.setFirstLockDuration(Duration.ofMinutes(7));
        properties.setSecondLockDuration(Duration.ofDays(9));
        properties.setFinalLockDuration(Duration.ofDays(11));
        properties.setLockedMessagePrefix("Locked until ");
        properties.setLockedMessageSuffix(". Ask admin.");

        service = new LoginLockoutService(properties);
    }

    @Test
    void recordFailedLogin_usesConfiguredFirstThresholdAndDuration() {
        User user = user();
        OffsetDateTime now = OffsetDateTime.now();

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.recordFailedLogin(user, now)
        );

        assertEquals(1, user.getFailedLoginAttempts());
        assertEquals(0, user.getLoginLockLevel());

        AccountLockedException exception = assertThrows(
                AccountLockedException.class,
                () -> service.recordFailedLogin(user, now)
        );

        assertTrue(exception.getMessage().startsWith("Locked until "));
        assertTrue(exception.getMessage().endsWith(". Ask admin."));
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(1, user.getLoginLockLevel());
        assertEquals(now.plusMinutes(7), user.getLockedUntil());
    }

    @Test
    void recordFailedLogin_usesConfiguredEscalatedDurations() {
        User user = user();
        OffsetDateTime now = OffsetDateTime.now();
        ReflectionTestUtils.setField(user, "loginLockLevel", 1);
        user.setFailedLoginAttempts(1);

        assertThrows(
                AccountLockedException.class,
                () -> service.recordFailedLogin(user, now)
        );

        assertEquals(2, user.getLoginLockLevel());
        assertEquals(now.plusDays(9), user.getLockedUntil());

        user.clearLoginLock();
        ReflectionTestUtils.setField(user, "loginLockLevel", 2);
        user.setFailedLoginAttempts(1);

        assertThrows(
                AccountLockedException.class,
                () -> service.recordFailedLogin(user, now)
        );

        assertEquals(3, user.getLoginLockLevel());
        assertEquals(now.plusDays(11), user.getLockedUntil());
    }

    @Test
    void checkLoginAllowed_whenLocked_throwsConfiguredMessage() {
        User user = user();
        OffsetDateTime now = OffsetDateTime.now();
        user.setLockedUntil(now.plusMinutes(5));

        AccountLockedException exception = assertThrows(
                AccountLockedException.class,
                () -> service.checkLoginAllowed(user, now)
        );

        assertTrue(exception.getMessage().startsWith("Locked until "));
        assertTrue(exception.getMessage().endsWith(". Ask admin."));
    }

    @Test
    void recordSuccessfulLogin_clearsAttemptsLockAndEscalation() {
        User user = user();
        user.setFailedLoginAttempts(1);
        user.setLockedUntil(OffsetDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(user, "loginLockLevel", 2);

        service.recordSuccessfulLogin(user);

        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(0, user.getLoginLockLevel());
        assertNull(user.getLockedUntil());
    }

    private static User user() {
        User user = new User(
                1L,
                "teacher01",
                "Rahul",
                Set.of(UserRole.TEACHER)
        );
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
