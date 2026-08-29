package com.dawnrise.identity.securityaudit.dto;

import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;

import java.time.OffsetDateTime;

public class SecurityAuditEventResponse {

    private Long id;
    private Long organizationId;
    private Long actorUserId;
    private SecurityAuditAction action;
    private SecurityAuditOutcome outcome;
    private String targetType;
    private Long targetId;
    private String requestId;
    private String ipAddress;
    private String details;
    private OffsetDateTime occurredAt;

    public SecurityAuditEventResponse() {
    }

    public SecurityAuditEventResponse(
            Long id,
            Long organizationId,
            Long actorUserId,
            SecurityAuditAction action,
            SecurityAuditOutcome outcome,
            String targetType,
            Long targetId,
            String requestId,
            String ipAddress,
            String details,
            OffsetDateTime occurredAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.outcome = outcome;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getActorUserId() { return actorUserId; }
    public SecurityAuditAction getAction() { return action; }
    public SecurityAuditOutcome getOutcome() { return outcome; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getRequestId() { return requestId; }
    public String getIpAddress() { return ipAddress; }
    public String getDetails() { return details; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
