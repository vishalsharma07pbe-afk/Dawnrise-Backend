package com.dawnrise.identity.platform.audit.service;

import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;

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
