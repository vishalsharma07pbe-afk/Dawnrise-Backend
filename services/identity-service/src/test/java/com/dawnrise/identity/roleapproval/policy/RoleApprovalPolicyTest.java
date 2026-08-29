package com.dawnrise.identity.roleapproval.policy;

import com.dawnrise.identity.user.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleApprovalPolicyTest {

    private final RoleApprovalPolicy policy = new RoleApprovalPolicy();

    @Test
    void requiresApproval_distinguishesSensitiveAndRoutineRoles() {
        assertTrue(policy.requiresApproval(UserRole.ADMIN));
        assertTrue(policy.requiresApproval(UserRole.PRINCIPAL));
        assertFalse(policy.requiresApproval(UserRole.TEACHER));
    }

    @Test
    void canRequestApproval_allowsEligibleRequesterRolesForSensitiveRoles() {
        assertTrue(policy.canRequestApproval(
                Set.of(UserRole.HR),
                UserRole.ADMIN
        ));
        assertTrue(policy.canRequestApproval(
                Set.of(UserRole.ADMIN),
                UserRole.PRINCIPAL
        ));
        assertTrue(policy.canRequestApproval(
                Set.of(UserRole.GOVERNING_AUTHORITY),
                UserRole.ADMIN
        ));
        assertFalse(policy.canRequestApproval(
                Set.of(UserRole.TEACHER),
                UserRole.ADMIN
        ));
        assertFalse(policy.canRequestApproval(
                Set.of(UserRole.HR),
                UserRole.TEACHER
        ));
    }

    @Test
    void findAvailableApproverRole_returnsUnsatisfiedRoleInStableOrder() {
        assertEquals(
                UserRole.ADMIN,
                policy.findAvailableApproverRole(
                        UserRole.ADMIN,
                        Set.of(UserRole.ADMIN, UserRole.GOVERNING_AUTHORITY),
                        Set.of()
                ).orElseThrow()
        );

        assertEquals(
                UserRole.GOVERNING_AUTHORITY,
                policy.findAvailableApproverRole(
                        UserRole.ADMIN,
                        Set.of(UserRole.ADMIN, UserRole.GOVERNING_AUTHORITY),
                        Set.of(UserRole.ADMIN)
                ).orElseThrow()
        );
    }

    @Test
    void getSensitiveAndRoutineRoles_splitRequestedRoles() {
        Set<UserRole> requestedRoles = Set.of(
                UserRole.ADMIN,
                UserRole.TEACHER,
                UserRole.ACCOUNTANT
        );

        assertEquals(
                Set.of(UserRole.ADMIN, UserRole.ACCOUNTANT),
                policy.getSensitiveRoles(requestedRoles)
        );
        assertEquals(
                Set.of(UserRole.TEACHER),
                policy.getRoutineRoles(requestedRoles)
        );
    }
}
