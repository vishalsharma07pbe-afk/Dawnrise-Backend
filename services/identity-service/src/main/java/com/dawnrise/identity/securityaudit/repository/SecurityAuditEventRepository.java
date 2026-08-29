package com.dawnrise.identity.securityaudit.repository;

import com.dawnrise.identity.securityaudit.entity.SecurityAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository
        extends JpaRepository<SecurityAuditEvent, Long> {

    Page<SecurityAuditEvent> findAllByOrganizationId(
            Long organizationId,
            Pageable pageable
    );
}
