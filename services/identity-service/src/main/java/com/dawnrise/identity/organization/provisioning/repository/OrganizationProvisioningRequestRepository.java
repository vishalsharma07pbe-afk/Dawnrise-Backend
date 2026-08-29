package com.dawnrise.identity.organization.provisioning.repository;

import com.dawnrise.identity.organization.provisioning.entity.OrganizationProvisioningRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationProvisioningRequestRepository
        extends JpaRepository<OrganizationProvisioningRequest, Long> {

    Optional<OrganizationProvisioningRequest>
    findByIdempotencyKey(
            String idempotencyKey
    );

    List<OrganizationProvisioningRequest>
    findAllByOrganizationIdOrderByCreatedAtDesc(
            Long organizationId
    );
}