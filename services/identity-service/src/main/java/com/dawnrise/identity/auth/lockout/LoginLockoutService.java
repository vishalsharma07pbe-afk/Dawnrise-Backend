package com.dawnrise.identity.auth.lockout;

import com.dawnrise.identity.auth.exception.AccountLockedException;
import com.dawnrise.identity.auth.exception.InvalidCredentialsException;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.user.entity.User;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class LoginLockoutService {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Invalid username or password";

    private final LoginLockoutProperties properties;

    public LoginLockoutService(LoginLockoutProperties properties) {
        this.properties = properties;
    }

    public void checkLoginAllowed(
            User user,
            OffsetDateTime currentTime
    ) {
        if (user.isLoginLockedAt(currentTime)) {
            throw lockedException(user);
        }
    }

    public void recordFailedLogin(
            User user,
            OffsetDateTime currentTime
    ) {
        user.recordFailedLoginAttempt();

        int failureThreshold = user.getLoginLockLevel() == 0
                ? properties.getFirstFailureThreshold()
                : properties.getEscalatedFailureThreshold();

        if (user.getFailedLoginAttempts() >= failureThreshold) {
            user.lockLoginUntil(
                    lockExpiresAt(user.getLoginLockLevel() + 1, currentTime)
            );

            throw lockedException(user);
        }

        throw new InvalidCredentialsException(
                INVALID_CREDENTIALS_MESSAGE
        );
    }

    public void recordSuccessfulLogin(User user) {
        user.clearLoginLock();
    }

    public void checkLoginAllowed(
            PlatformUser user,
            OffsetDateTime currentTime
    ) {
        if (user.isLoginLockedAt(currentTime)) {
            throw lockedException(user);
        }
    }

    public void recordFailedLogin(
            PlatformUser user,
            OffsetDateTime currentTime
    ) {
        user.recordFailedLoginAttempt();

        int failureThreshold = user.getLoginLockLevel() == 0
                ? properties.getFirstFailureThreshold()
                : properties.getEscalatedFailureThreshold();

        if (user.getFailedLoginAttempts() >= failureThreshold) {
            user.lockLoginUntil(
                    lockExpiresAt(user.getLoginLockLevel() + 1, currentTime)
            );

            throw lockedException(user);
        }

        throw new InvalidCredentialsException(
                INVALID_CREDENTIALS_MESSAGE
        );
    }

    public void recordSuccessfulLogin(PlatformUser user) {
        user.clearLoginLock();
    }

    private OffsetDateTime lockExpiresAt(
            int nextLockLevel,
            OffsetDateTime currentTime
    ) {
        if (nextLockLevel == 1) {
            return currentTime.plus(properties.getFirstLockDuration());
        }

        if (nextLockLevel == 2) {
            return currentTime.plus(properties.getSecondLockDuration());
        }

        return currentTime.plus(properties.getFinalLockDuration());
    }

    private AccountLockedException lockedException(User user) {
        OffsetDateTime lockedUntil = user.getLockedUntil()
                .truncatedTo(ChronoUnit.SECONDS);

        return new AccountLockedException(
                properties.getLockedMessagePrefix()
                        + lockedUntil
                        + properties.getLockedMessageSuffix()
        );
    }

    private AccountLockedException lockedException(PlatformUser user) {
        OffsetDateTime lockedUntil = user.getLockedUntil()
                .truncatedTo(ChronoUnit.SECONDS);

        return new AccountLockedException(
                properties.getLockedMessagePrefix()
                        + lockedUntil
                        + properties.getLockedMessageSuffix()
        );
    }
}
