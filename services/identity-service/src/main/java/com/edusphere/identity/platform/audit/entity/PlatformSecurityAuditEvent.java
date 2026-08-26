package com.edusphere.identity.platform.audit.entity;

import com.edusphere.identity.securityaudit.enums.SecurityAuditAction;
import com.edusphere.identity.securityaudit.enums.SecurityAuditOutcome;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_security_audit_events")
public class PlatformSecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_platform_user_id")
    private Long actorPlatformUserId;

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
    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime occurredAt;

    protected PlatformSecurityAuditEvent() {
    }

    public PlatformSecurityAuditEvent(
            Long actorPlatformUserId,
            SecurityAuditAction action,
            SecurityAuditOutcome outcome,
            String targetType,
            Long targetId,
            String requestId,
            String ipAddress,
            String details
    ) {
        this.actorPlatformUserId = actorPlatformUserId;
        this.action = action;
        this.outcome = outcome;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.details = details;
    }
}
