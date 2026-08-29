package com.dawnrise.identity.securityaudit.service;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.securityaudit.dto.SecurityAuditEventResponse;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface SecurityAuditService {

    void record(
            Long organizationId,
            Long actorUserId,
            SecurityAuditAction action,
            SecurityAuditOutcome outcome,
            String targetType,
            Long targetId,
            Map<String, ?> details
    );

    PageResponse<SecurityAuditEventResponse> getEvents(
            Long organizationId,
            Pageable pageable
    );
}
