package com.dawnrise.identity.roleapproval.repository;

import com.dawnrise.identity.roleapproval.entity.RoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleAssignmentRequestRepository
        extends JpaRepository<RoleAssignmentRequest, Long> {

    Optional<RoleAssignmentRequest> findByIdAndOrganizationId(
            Long requestId,
            Long organizationId
    );

    boolean existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
            Long organizationId,
            Long userId,
            UserRole requestedRole,
            ApprovalStatus status
    );

    Page<RoleAssignmentRequest>
    findAllByOrganizationIdAndStatusAndRequestedRoleIn(
            Long organizationId,
            ApprovalStatus status,
            Collection<UserRole> requestedRoles,
            Pageable pageable
    );

    /*
     * Returns pending requests that this approver may review.
     *
     * Excludes:
     * 1. Requests created by the approver.
     * 2. Requests where the approver is the target user.
     * 3. Requests already reviewed by this approver.
     */
    @Query("""
        SELECT request
        FROM RoleAssignmentRequest request
        WHERE request.organizationId = :organizationId
          AND request.status = :status
          AND request.requestedRole IN :reviewableRoles
          AND request.requestedByUserId <> :approverUserId
          AND request.userId <> :approverUserId
          AND NOT EXISTS (
              SELECT approval.id
              FROM RoleAssignmentApproval approval
              WHERE approval.requestId = request.id
                AND approval.approverUserId = :approverUserId
          )
        """)
    Page<RoleAssignmentRequest> findActionableRequestsForApprover(
            @Param("organizationId") Long organizationId,
            @Param("status") ApprovalStatus status,
            @Param("reviewableRoles") Set<UserRole> reviewableRoles,
            @Param("approverUserId") Long approverUserId,
            Pageable pageable
    );

    boolean existsByOrganizationIdAndUserIdAndStatusAndIdNot(
            Long organizationId,
            Long userId,
            ApprovalStatus status,
            Long excludedRequestId
    );
}