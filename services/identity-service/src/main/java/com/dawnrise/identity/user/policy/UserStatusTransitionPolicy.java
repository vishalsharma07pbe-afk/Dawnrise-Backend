package com.dawnrise.identity.user.policy;

import com.dawnrise.identity.user.enums.UserStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class UserStatusTransitionPolicy {

    /*
     * Only operational states can be changed by administrators.
     * Onboarding states are controlled by approval and activation flows.
     */
    private static final Map<UserStatus, Set<UserStatus>>
            ADMINISTRATIVE_TRANSITIONS = Map.of(
            UserStatus.ACTIVE,
            Set.of(
                    UserStatus.SUSPENDED,
                    UserStatus.INACTIVE
            ),
            UserStatus.SUSPENDED,
            Set.of(
                    UserStatus.ACTIVE,
                    UserStatus.INACTIVE
            )
    );

    public boolean canTransitionAdministratively(
            UserStatus currentStatus,
            UserStatus requestedStatus
    ) {
        // Repeating the current status is a safe idempotent operation.
        if (currentStatus == requestedStatus) {
            return true;
        }

        return ADMINISTRATIVE_TRANSITIONS
                .getOrDefault(
                        currentStatus,
                        Set.of()
                )
                .contains(requestedStatus);
    }
}
