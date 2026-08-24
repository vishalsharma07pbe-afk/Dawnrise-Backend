package com.edusphere.identity.platform.audit.repository;

import com.edusphere.identity.platform.audit.entity.PlatformSecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSecurityAuditEventRepository
        extends JpaRepository<PlatformSecurityAuditEvent, Long> {
}
