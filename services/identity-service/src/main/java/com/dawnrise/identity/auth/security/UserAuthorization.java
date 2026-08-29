package com.dawnrise.identity.auth.security;

import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.policy.UserStatusAuthorizationPolicy;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component("userAuthorization")
public class UserAuthorization {

    private static final Set<UserRole> ADMISSIONS_ALLOWED_ROLES =
            Set.of(
                    UserRole.STUDENT,
                    UserRole.PARENT
            );

    private static final Set<UserRole> HR_ALLOWED_ROLES =
            Set.of(
                    UserRole.TEACHER,
                    UserRole.ACCOUNTANT,
                    UserRole.LIBRARIAN,
                    UserRole.EXAMINATION_CONTROLLER,
                    UserRole.TRANSPORT_MANAGER,
                    UserRole.INVENTORY_MANAGER,
                    UserRole.SPORTS_STAFF,
                    UserRole.SCIENCE_LAB_STAFF,
                    UserRole.COMPUTER_LAB_STAFF,
                    UserRole.MUSIC_STAFF,
                    UserRole.MAINTENANCE_STAFF,
                    UserRole.HOUSEKEEPING_STAFF,
                    UserRole.HOSTEL_STAFF
            );

    private final UserRepository userRepository;
    private final UserStatusAuthorizationPolicy
            statusAuthorizationPolicy;

    public UserAuthorization(
            UserRepository userRepository,
            UserStatusAuthorizationPolicy statusAuthorizationPolicy
    ) {
        this.userRepository = userRepository;
        this.statusAuthorizationPolicy =
                statusAuthorizationPolicy;
    }

    public boolean canCreateUser(
            Authentication authentication,
            Set<UserRole> requestedRoles
    ) {
        if (authentication == null
                || requestedRoles == null
                || requestedRoles.isEmpty()) {
            return false;
        }

        if (hasAnyRole(
                authentication,
                "ROLE_ADMIN",
                "ROLE_PRINCIPAL"
        )) {
            return true;
        }

        Set<UserRole> allowedRoles = new HashSet<>();

        if (hasRole(
                authentication,
                "ROLE_ADMISSIONS_OFFICER"
        )) {
            allowedRoles.addAll(
                    ADMISSIONS_ALLOWED_ROLES
            );
        }

        if (hasRole(
                authentication,
                "ROLE_HR"
        )) {
            allowedRoles.addAll(
                    HR_ALLOWED_ROLES
            );
        }

        return allowedRoles.containsAll(
                requestedRoles
        );
    }

    public boolean canUpdateStatus(
            Authentication authentication,
            Long organizationId,
            Long userId,
            UserStatus requestedStatus
    ) {
        if (authentication == null
                || organizationId == null
                || userId == null
                || requestedStatus == null) {
            return false;
        }

        User targetUser = userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        userId
                )
                .orElse(null);

        if (targetUser == null) {
            return false;
        }

        return statusAuthorizationPolicy.canUpdateStatus(
                targetUser.getStatus(),
                requestedStatus,
                permissionCode -> hasAuthority(
                        authentication,
                        permissionCode.name()
                )
        );
    }

    private boolean hasAnyRole(
            Authentication authentication,
            String... roles
    ) {
        for (String role : roles) {
            if (hasRole(authentication, role)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasRole(
            Authentication authentication,
            String requiredRole
    ) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority()
                                .equals(requiredRole)
                );
    }

    private boolean hasAuthority(
            Authentication authentication,
            String requiredAuthority
    ) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority()
                                .equals(requiredAuthority)
                );
    }
}