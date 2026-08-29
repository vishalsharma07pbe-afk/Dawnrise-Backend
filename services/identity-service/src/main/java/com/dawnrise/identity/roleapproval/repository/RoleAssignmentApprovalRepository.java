package com.dawnrise.identity.roleapproval.repository;

import com.dawnrise.identity.roleapproval.entity.RoleAssignmentApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface RoleAssignmentApprovalRepository
        extends JpaRepository<RoleAssignmentApproval, Long> {

    List<RoleAssignmentApproval> findAllByRequestId(
            Long requestId
    );

    boolean existsByRequestIdAndApproverUserId(
            Long requestId,
            Long approverUserId
    );

    Optional<RoleAssignmentApproval>
        findByRequestIdAndApproverUserId(
            Long requestId,
            Long approverUserId
    );

    Page<RoleAssignmentApproval>
        findAllByApproverUserId(
            Long approverUserId,
            Pageable pageable
    );

    @Query("""
            select approval
            from RoleAssignmentApproval approval
            join RoleAssignmentRequest request
                on request.id = approval.requestId
            where request.organizationId = :organizationId
                and approval.approverUserId = :approverUserId
            """)
    Page<RoleAssignmentApproval> findDecisionHistoryForApprover(
            @Param("organizationId") Long organizationId,
            @Param("approverUserId") Long approverUserId,
            Pageable pageable
    );

    List<RoleAssignmentApproval> findAllByRequestIdOrderByDecidedAtAsc(
            Long requestId
    );
}
