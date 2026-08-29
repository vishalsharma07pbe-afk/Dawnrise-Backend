package com.dawnrise.identity.securityaudit.entity;

import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

@Entity
@Immutable
@Table(name = "security_audit_events")
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private SecurityAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SecurityAuditOutcome outcome;

    @Column(name = "target_type", nullable = false, length = 80)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "request_id", length = 120)
    private String requestId;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(length = 1000)
    private String details;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    public SecurityAuditEvent() {
    }

    public SecurityAuditEvent(
            Long organizationId,
            Long actorUserId,
            SecurityAuditAction action,
            SecurityAuditOutcome outcome,
            String targetType,
            Long targetId,
            String requestId,
            String ipAddress,
            String details
    ) {
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.outcome = outcome;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.details = details;
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
