package com.dawnrise.identity.roleremoval.policy;

import com.dawnrise.identity.roleapproval.policy.RoleApprovalPolicy;
import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class RoleRemovalApprovalPolicy {

    private final RoleApprovalPolicy roleApprovalPolicy;

    public RoleRemovalApprovalPolicy(RoleApprovalPolicy roleApprovalPolicy) {
        this.roleApprovalPolicy = roleApprovalPolicy;
    }

    public boolean requiresApproval(UserRole requestedRole) {
        return roleApprovalPolicy.requiresApproval(requestedRole);
    }

    public Set<UserRole> getRequiredApproverRoles(UserRole requestedRole) {
        return roleApprovalPolicy.getRequiredApproverRoles(requestedRole);
    }

    public Set<UserRole> getReviewableRoles(Set<UserRole> approverRoles) {
        return roleApprovalPolicy.getReviewableRoles(approverRoles);
    }

    public boolean canRequestRemoval(
            Set<UserRole> requesterRoles,
            UserRole requestedRole
    ) {
        return roleApprovalPolicy.canRequestApproval(
                requesterRoles,
                requestedRole
        );
    }

    public Optional<UserRole> findAvailableApproverRole(
            UserRole requestedRole,
            Set<UserRole> approverRoles,
            Set<UserRole> alreadySatisfiedRoles
    ) {
        return roleApprovalPolicy.findAvailableApproverRole(
                requestedRole,
                approverRoles,
                alreadySatisfiedRoles
        );
    }
}
