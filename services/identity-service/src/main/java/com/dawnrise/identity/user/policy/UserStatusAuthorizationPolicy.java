package com.dawnrise.identity.user.policy;

import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.user.enums.UserStatus;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
public class UserStatusAuthorizationPolicy {

    private final UserStatusTransitionPolicy transitionPolicy;

    public UserStatusAuthorizationPolicy(
            UserStatusTransitionPolicy transitionPolicy
    ) {
        this.transitionPolicy = transitionPolicy;
    }

    public boolean canUpdateStatus(
            UserStatus currentStatus,
            UserStatus requestedStatus,
            Predicate<PermissionCode> hasPermission
    ) {
        if (currentStatus == null
                || requestedStatus == null
                || hasPermission == null) {
            return false;
        }

        if (!transitionPolicy.canTransitionAdministratively(
                currentStatus,
                requestedStatus
        )) {
            return false;
        }

        if (currentStatus == requestedStatus) {
            return hasPermission.test(
                    PermissionCode.USER_SUSPEND
            ) || hasPermission.test(
                    PermissionCode.USER_DEACTIVATE
            ) || hasPermission.test(
                    PermissionCode.USER_REACTIVATE
            );
        }

        if (currentStatus == UserStatus.ACTIVE
                && requestedStatus == UserStatus.SUSPENDED) {
            return hasPermission.test(
                    PermissionCode.USER_SUSPEND
            );
        }

        if ((currentStatus == UserStatus.ACTIVE
                || currentStatus == UserStatus.SUSPENDED)
                && requestedStatus == UserStatus.INACTIVE) {
            return hasPermission.test(
                    PermissionCode.USER_DEACTIVATE
            );
        }

        if (currentStatus == UserStatus.SUSPENDED
                && requestedStatus == UserStatus.ACTIVE) {
            return hasPermission.test(
                    PermissionCode.USER_REACTIVATE
            );
        }

        return false;
    }
}