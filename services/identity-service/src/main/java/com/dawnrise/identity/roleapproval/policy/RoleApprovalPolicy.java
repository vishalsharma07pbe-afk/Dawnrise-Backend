package com.dawnrise.identity.roleapproval.policy;

import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.Optional;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class RoleApprovalPolicy {

    /*
     * Maps a requested role to the roles required to approve it.
     *
     * Example:
     * ADMIN -> [ADMIN, PRINCIPAL]
     *
     * This means assigning ADMIN requires approval from both
     * an existing administrator and a principal.
     *
     * Roles absent from this map are treated as routine roles
     * that do not require this approval workflow.
     */
    private static final Map<UserRole, Set<UserRole>>
            REQUIRED_APPROVERS = new EnumMap<>(UserRole.class);

    static {
        /*
         * Assigning ADMIN requires:
         *
         * 1. An existing ADMIN for operational/security verification.
         * 2. A GOVERNING_AUTHORITY for independent authorization.
         *
         * The two approvals must come from two different users.
         */
        REQUIRED_APPROVERS.put(
                UserRole.ADMIN,
                Set.of(
                        UserRole.ADMIN,
                        UserRole.GOVERNING_AUTHORITY
                )
        );

        /*
         * Assigning PRINCIPAL requires:
         *
         * 1. ADMIN verification of the account and employment link.
         * 2. GOVERNING_AUTHORITY approval of the appointment.
         */
        REQUIRED_APPROVERS.put(
                UserRole.PRINCIPAL,
                Set.of(
                        UserRole.ADMIN,
                        UserRole.GOVERNING_AUTHORITY
                )
        );

        /*
         * Vice Principal is an academic leadership appointment.
         * Principal provides the required academic approval.
         */
        REQUIRED_APPROVERS.put(
                UserRole.VICE_PRINCIPAL,
                Set.of(UserRole.PRINCIPAL)
        );

        /*
         * HR has access to sensitive employee information and
         * account-provisioning workflows.
         */
        REQUIRED_APPROVERS.put(
                UserRole.HR,
                Set.of(
                        UserRole.ADMIN,
                        UserRole.PRINCIPAL
                )
        );

        /*
         * Admissions Officer manages sensitive applicant,
         * Student and Guardian information.
         */
        REQUIRED_APPROVERS.put(
                UserRole.ADMISSIONS_OFFICER,
                Set.of(UserRole.PRINCIPAL)
        );

        /*
         * Accountant receives access to financial data and
         * payment-processing workflows.
         */
        REQUIRED_APPROVERS.put(
                UserRole.ACCOUNTANT,
                Set.of(
                        UserRole.ADMIN,
                        UserRole.PRINCIPAL
                )
        );

        /*
         * Examination Controller manages marks workflows,
         * moderation and result-publication preparation.
         */
        REQUIRED_APPROVERS.put(
                UserRole.EXAMINATION_CONTROLLER,
                Set.of(UserRole.PRINCIPAL)
        );

        /*
         * Additional Governing Authorities require approval from:
         *
         * 1. An existing GOVERNING_AUTHORITY.
         * 2. An ADMIN for account/security verification.
         *
         * The first Governing Authority must be created through
         * the organization bootstrap/onboarding process.
         */
        REQUIRED_APPROVERS.put(
                UserRole.GOVERNING_AUTHORITY,
                Set.of(
                        UserRole.GOVERNING_AUTHORITY,
                        UserRole.ADMIN
                )
        );
    }

    /*
     * Returns true when the requested role is included in the
     * approval-policy map.
     *
     * Example:
     * ADMIN  -> true
     * TEACHER -> false
     */
    public boolean requiresApproval(UserRole requestedRole) {
        return REQUIRED_APPROVERS.containsKey(requestedRole);
    }

    /*
     * Returns the roles required to approve the requested role.
     *
     * An empty set means that the role does not use this
     * approval workflow.
     */
    public Set<UserRole> getRequiredApproverRoles(
            UserRole requestedRole
    ) {
        return REQUIRED_APPROVERS.getOrDefault(
                requestedRole,
                Set.of()
        );
    }

    /*
     * Calculates which role requests the logged-in approver is allowed to review.
     *
     * Example:
     * If the user has PRINCIPAL, this method returns every
     * requested role whose approval rules include PRINCIPAL.
     */
    public Set<UserRole> getReviewableRoles(
            Set<UserRole> approverRoles
    ) {
        Set<UserRole> reviewableRoles = new HashSet<>();

        // Inspect every requested-role -> required-approvers rule.
        for (Map.Entry<UserRole, Set<UserRole>> entry
                : REQUIRED_APPROVERS.entrySet()) {

            Set<UserRole> requiredApprovers = entry.getValue();

            /*
             * The user can review the request when at least one of
             * their roles appears in the required-approvers set.
             */
            boolean canReview = requiredApprovers
                    .stream()
                    .anyMatch(approverRoles::contains);

            if (canReview) {
                // entry.getKey() is the role being requested.
                reviewableRoles.add(entry.getKey());
            }
        }

        return reviewableRoles;
    }

    /*
     * Determines whether the requester is allowed to submit an
     * approval request for the specified role.
     *
     * HR handles employee provisioning. ADMIN is also allowed
     * for initial organization setup and exceptional cases.
     */
    public boolean canRequestApproval(
            Set<UserRole> requesterRoles,
            UserRole requestedRole
    ) {
        if (requesterRoles == null || requesterRoles.isEmpty()) {
            return false;
        }

        if (requestedRole == null || !requiresApproval(requestedRole)) {
            return false;
        }

        /*
         * HR may request approved staff appointments.
         */
        if (requesterRoles.contains(UserRole.HR)) {
            return true;
        }

        /*
         * Admin may initiate account and role provisioning.
         */
        if (requesterRoles.contains(UserRole.ADMIN)) {
            return true;
        }

        /*
         * Principal may request academic leadership and
         * academic-operation appointments.
         */
        if (requesterRoles.contains(UserRole.PRINCIPAL)) {
            return requestedRole == UserRole.VICE_PRINCIPAL
                    || requestedRole == UserRole.ADMISSIONS_OFFICER
                    || requestedRole == UserRole.EXAMINATION_CONTROLLER;
        }

        /*
         * Governing Authority may initiate senior leadership
         * appointments.
         */
        if (requesterRoles.contains(UserRole.GOVERNING_AUTHORITY)) {
            return requestedRole == UserRole.ADMIN
                    || requestedRole == UserRole.PRINCIPAL
                    || requestedRole == UserRole.GOVERNING_AUTHORITY;
        }

        return false;
    }

    /*
     * Finds one of the logged-in user's roles that can satisfy an
     * outstanding approval requirement for the requested role.
     *
     * alreadySatisfiedRoles contains approver roles for which an
     * approval has already been recorded.
     */
    public Optional<UserRole> findAvailableApproverRole(
            UserRole requestedRole,
            Set<UserRole> approverRoles,
            Set<UserRole> alreadySatisfiedRoles
    ) {
        if (requestedRole == null ||
                approverRoles == null ||
                alreadySatisfiedRoles == null) {
            return Optional.empty();
        }

        Set<UserRole> requiredApproverRoles =
                getRequiredApproverRoles(requestedRole);

        return requiredApproverRoles
                .stream()

                // Keep only roles that the logged-in user actually has.
                .filter(approverRoles::contains)

                // Ignore approval requirements already satisfied.
                .filter(role -> !alreadySatisfiedRoles.contains(role))

                // Make the selected result predictable.
                .sorted(Comparator.comparingInt(Enum::ordinal))

                // Return one matching role, or Optional.empty().
                .findFirst();
    }

    public Set<UserRole> getSensitiveRoles(Set<UserRole> requestedRoles) {
        Set<UserRole> sensitiveRoles = new HashSet<>();

        if (requestedRoles == null) {
            return sensitiveRoles;
        }

        // A role is sensitive when it exists in the approval-policy map.
        for (UserRole role : requestedRoles) {
            if (requiresApproval(role)) {
                sensitiveRoles.add(role);
            }
        }

        return sensitiveRoles;
    }

    public Set<UserRole> getRoutineRoles(Set<UserRole> requestedRoles) {
        Set<UserRole> routineRoles = new HashSet<>();

        if (requestedRoles == null) {
            return routineRoles;
        }

        // Roles absent from the approval-policy map can be assigned directly.
        for (UserRole role : requestedRoles) {
            if (!requiresApproval(role)) {
                routineRoles.add(role);
            }
        }

        return routineRoles;
    }
}