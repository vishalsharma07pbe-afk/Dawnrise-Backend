package com.dawnrise.identity.user.policy;

import com.dawnrise.identity.user.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserStatusTransitionPolicyTest {

    private final UserStatusTransitionPolicy policy =
            new UserStatusTransitionPolicy();

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void canTransitionAdministratively_whenStatusUnchanged_returnsTrue(
            UserStatus status
    ) {
        assertTrue(policy.canTransitionAdministratively(status, status));
    }

    @ParameterizedTest
    @MethodSource("allowedAdministrativeTransitions")
    void canTransitionAdministratively_whenTransitionAllowed_returnsTrue(
            UserStatus currentStatus,
            UserStatus requestedStatus
    ) {
        assertTrue(policy.canTransitionAdministratively(
                currentStatus,
                requestedStatus
        ));
    }

    @ParameterizedTest
    @MethodSource("blockedAdministrativeTransitions")
    void canTransitionAdministratively_whenTransitionBlocked_returnsFalse(
            UserStatus currentStatus,
            UserStatus requestedStatus
    ) {
        assertFalse(policy.canTransitionAdministratively(
                currentStatus,
                requestedStatus
        ));
    }

    @Test
    void canTransitionAdministratively_whenLockedToActive_returnsFalse() {
        assertFalse(policy.canTransitionAdministratively(
                UserStatus.LOCKED,
                UserStatus.ACTIVE
        ));
    }

    private static Stream<UserStatus[]> allowedAdministrativeTransitions() {
        return Stream.of(
                transition(UserStatus.ACTIVE, UserStatus.SUSPENDED),
                transition(UserStatus.ACTIVE, UserStatus.INACTIVE),
                transition(UserStatus.SUSPENDED, UserStatus.ACTIVE),
                transition(UserStatus.SUSPENDED, UserStatus.INACTIVE)
        );
    }

    private static Stream<UserStatus[]> blockedAdministrativeTransitions() {
        return Stream.of(
                transition(UserStatus.PENDING_APPROVAL, UserStatus.ACTIVE),
                transition(UserStatus.PENDING_ACTIVATION, UserStatus.ACTIVE),
                transition(UserStatus.ACTIVE, UserStatus.PENDING_APPROVAL),
                transition(UserStatus.ACTIVE, UserStatus.PENDING_ACTIVATION),
                transition(UserStatus.ACTIVE, UserStatus.LOCKED),
                transition(UserStatus.INACTIVE, UserStatus.ACTIVE),
                transition(UserStatus.INACTIVE, UserStatus.SUSPENDED),
                transition(UserStatus.SUSPENDED, UserStatus.PENDING_APPROVAL),
                transition(UserStatus.SUSPENDED, UserStatus.PENDING_ACTIVATION),
                transition(UserStatus.SUSPENDED, UserStatus.LOCKED),
                transition(UserStatus.LOCKED, UserStatus.SUSPENDED),
                transition(UserStatus.LOCKED, UserStatus.INACTIVE)
        );
    }

    private static UserStatus[] transition(
            UserStatus currentStatus,
            UserStatus requestedStatus
    ) {
        return new UserStatus[]{currentStatus, requestedStatus};
    }
}
