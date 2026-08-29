package com.dawnrise.identity.roleremoval.repository;

import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleremoval.entity.RoleRemovalRequest;
import com.dawnrise.identity.user.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface RoleRemovalRequestRepository
        extends JpaRepository<RoleRemovalRequest, Long> {

    Optional<RoleRemovalRequest> findByIdAndOrganizationId(
            Long requestId,
            Long organizationId
    );

    boolean existsByOrganizationIdAndUserIdAndRequestedRoleAndStatus(
            Long organizationId,
            Long userId,
            UserRole requestedRole,
            ApprovalStatus status
    );

    Page<RoleRemovalRequest> findAllByOrganizationIdAndRequestedByUserId(
            Long organizationId,
            Long requestedByUserId,
            Pageable pageable
    );

    Page<RoleRemovalRequest> findAllByOrganizationIdAndUserId(
            Long organizationId,
            Long userId,
            Pageable pageable
    );

    @Query("""
        SELECT request
        FROM RoleRemovalRequest request
        WHERE request.organizationId = :organizationId
          AND request.status = :status
          AND request.requestedRole IN :reviewableRoles
          AND request.requestedByUserId <> :approverUserId
          AND request.userId <> :approverUserId
          AND NOT EXISTS (
              SELECT approval.id
              FROM RoleRemovalApproval approval
              WHERE approval.requestId = request.id
                AND approval.approverUserId = :approverUserId
          )
        """)
    Page<RoleRemovalRequest> findActionableRequestsForApprover(
            @Param("organizationId") Long organizationId,
            @Param("status") ApprovalStatus status,
            @Param("reviewableRoles") Set<UserRole> reviewableRoles,
            @Param("approverUserId") Long approverUserId,
            Pageable pageable
    );
}
