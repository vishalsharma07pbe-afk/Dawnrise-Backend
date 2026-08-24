package com.edusphere.identity.platform.audit.service;

import com.edusphere.identity.securityaudit.enums.SecurityAuditAction;
import com.edusphere.identity.securityaudit.enums.SecurityAuditOutcome;

import java.util.Map;

public interface PlatformSecurityAuditService {

    void record(
            Long actorPlatformUserId,
            SecurityAuditAction action,
            SecurityAuditOutcome outcome,
            String targetType,
            Long targetId,
            Map<String, ?> details
    );
}
