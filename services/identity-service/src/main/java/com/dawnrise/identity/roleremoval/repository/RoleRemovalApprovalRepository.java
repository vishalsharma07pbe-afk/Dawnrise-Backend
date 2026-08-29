package com.dawnrise.identity.roleremoval.repository;

import com.dawnrise.identity.roleremoval.entity.RoleRemovalApproval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoleRemovalApprovalRepository
        extends JpaRepository<RoleRemovalApproval, Long> {

    List<RoleRemovalApproval> findAllByRequestId(Long requestId);

    List<RoleRemovalApproval> findAllByRequestIdOrderByDecidedAtAsc(
            Long requestId
    );

    boolean existsByRequestIdAndApproverUserId(
            Long requestId,
            Long approverUserId
    );

    @Query("""
            select approval
            from RoleRemovalApproval approval
            join RoleRemovalRequest request
                on request.id = approval.requestId
            where request.organizationId = :organizationId
                and approval.approverUserId = :approverUserId
            """)
    Page<RoleRemovalApproval> findDecisionHistoryForApprover(
            @Param("organizationId") Long organizationId,
            @Param("approverUserId") Long approverUserId,
            Pageable pageable
    );
}
