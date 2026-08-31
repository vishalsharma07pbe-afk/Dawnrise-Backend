package com.dawnrise.identity.profilechange.repository;

import com.dawnrise.identity.profilechange.entity.ProfileChangeRequest;
import com.dawnrise.identity.profilechange.enums.ProfileChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileChangeRequestRepository
        extends JpaRepository<ProfileChangeRequest, Long> {

    Optional<ProfileChangeRequest> findByOrganizationIdAndPublicId(
            Long organizationId,
            UUID publicId
    );

    boolean existsByOrganizationIdAndTargetUserIdAndStatus(
            Long organizationId,
            Long targetUserId,
            ProfileChangeRequestStatus status
    );

    Page<ProfileChangeRequest>
    findAllByOrganizationIdAndTargetUserId(
            Long organizationId,
            Long targetUserId,
            Pageable pageable
    );

    Page<ProfileChangeRequest>
    findAllByOrganizationIdAndTargetUserIdAndStatus(
            Long organizationId,
            Long targetUserId,
            ProfileChangeRequestStatus status,
            Pageable pageable
    );

    Page<ProfileChangeRequest>
    findAllByOrganizationIdAndRequestedByUserId(
            Long organizationId,
            Long requestedByUserId,
            Pageable pageable
    );

    Optional<ProfileChangeRequest>
    findFirstByOrganizationIdAndTargetUserIdAndStatus(
            Long organizationId,
            Long targetUserId,
            ProfileChangeRequestStatus status
    );
}