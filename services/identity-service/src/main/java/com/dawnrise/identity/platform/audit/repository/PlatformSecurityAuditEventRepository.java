package com.dawnrise.identity.platform.audit.repository;

import com.dawnrise.identity.platform.audit.entity.PlatformSecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSecurityAuditEventRepository
        extends JpaRepository<PlatformSecurityAuditEvent, Long> {
}
