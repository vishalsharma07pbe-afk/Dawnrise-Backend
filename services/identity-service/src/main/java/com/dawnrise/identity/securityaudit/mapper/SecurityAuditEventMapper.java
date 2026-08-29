package com.dawnrise.identity.securityaudit.mapper;

import com.dawnrise.identity.securityaudit.dto.SecurityAuditEventResponse;
import com.dawnrise.identity.securityaudit.entity.SecurityAuditEvent;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditEventMapper {

    public SecurityAuditEventResponse toResponse(
            SecurityAuditEvent event
    ) {
        return new SecurityAuditEventResponse(
                event.getId(),
                event.getOrganizationId(),
                event.getActorUserId(),
                event.getAction(),
                event.getOutcome(),
                event.getTargetType(),
                event.getTargetId(),
                event.getRequestId(),
                event.getIpAddress(),
                event.getDetails(),
                event.getOccurredAt()
        );
    }
}
