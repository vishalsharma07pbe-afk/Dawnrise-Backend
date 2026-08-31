package com.dawnrise.identity.profilechange.entity;

import com.dawnrise.identity.profilechange.enums.ProfileChangeRequestStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.util.UUID;

import java.time.OffsetDateTime;

@Entity
@Table(name = "profile_change_requests")
public class ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "original_first_name", nullable = false, length = 100)
    private String originalFirstName;

    @Column(name = "original_middle_name", length = 100)
    private String originalMiddleName;

    @Column(name = "original_last_name", length = 100)
    private String originalLastName;

    @Column(name = "original_email", length = 150)
    private String originalEmail;

    @Column(name = "original_phone", length = 20)
    private String originalPhone;

    @Column(name = "proposed_first_name", nullable = false, length = 100)
    private String proposedFirstName;

    @Column(name = "proposed_middle_name", length = 100)
    private String proposedMiddleName;

    @Column(name = "proposed_last_name", length = 100)
    private String proposedLastName;

    @Column(name = "proposed_email", length = 150)
    private String proposedEmail;

    @Column(name = "proposed_phone", length = 20)
    private String proposedPhone;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProfileChangeRequestStatus status =
            ProfileChangeRequestStatus.PENDING;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProfileChangeRequest() {
    }

    public ProfileChangeRequest(
            Long organizationId,
            Long targetUserId,
            Long requestedByUserId,
            String originalFirstName,
            String originalMiddleName,
            String originalLastName,
            String originalEmail,
            String originalPhone,
            String proposedFirstName,
            String proposedMiddleName,
            String proposedLastName,
            String proposedEmail,
            String proposedPhone,
            String reason,
            OffsetDateTime expiresAt
    ) {
        this.publicId = UUID.randomUUID();
        this.organizationId = organizationId;
        this.targetUserId = targetUserId;
        this.requestedByUserId = requestedByUserId;
        this.originalFirstName = originalFirstName;
        this.originalMiddleName = originalMiddleName;
        this.originalLastName = originalLastName;
        this.originalEmail = originalEmail;
        this.originalPhone = originalPhone;
        this.proposedFirstName = proposedFirstName;
        this.proposedMiddleName = proposedMiddleName;
        this.proposedLastName = proposedLastName;
        this.proposedEmail = proposedEmail;
        this.proposedPhone = proposedPhone;
        this.reason = reason;
        this.expiresAt = expiresAt;
        this.status = ProfileChangeRequestStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getVersion() {
        return version;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public Long getRequestedByUserId() {
        return requestedByUserId;
    }

    public String getOriginalFirstName() {
        return originalFirstName;
    }

    public String getOriginalMiddleName() {
        return originalMiddleName;
    }

    public String getOriginalLastName() {
        return originalLastName;
    }

    public String getOriginalEmail() {
        return originalEmail;
    }

    public String getOriginalPhone() {
        return originalPhone;
    }

    public String getProposedFirstName() {
        return proposedFirstName;
    }

    public String getProposedMiddleName() {
        return proposedMiddleName;
    }

    public String getProposedLastName() {
        return proposedLastName;
    }

    public String getProposedEmail() {
        return proposedEmail;
    }

    public String getProposedPhone() {
        return proposedPhone;
    }

    public String getReason() {
        return reason;
    }

    public ProfileChangeRequestStatus getStatus() {
        return status;
    }

    public Long getDecidedByUserId() {
        return decidedByUserId;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isPending() {
        return status == ProfileChangeRequestStatus.PENDING;
    }

    public boolean isExpiredAt(OffsetDateTime currentTime) {
        return isPending()
                && !expiresAt.isAfter(currentTime);
    }

    public void approve(
            Long decidedByUserId,
            String decisionReason,
            OffsetDateTime decidedAt
    ) {
        ensurePending();

        this.status = ProfileChangeRequestStatus.APPROVED;
        this.decidedByUserId = decidedByUserId;
        this.decisionReason = normalize(decisionReason);
        this.decidedAt = decidedAt;
    }

    public void reject(
            Long decidedByUserId,
            String decisionReason,
            OffsetDateTime decidedAt
    ) {
        ensurePending();

        this.status = ProfileChangeRequestStatus.REJECTED;
        this.decidedByUserId = decidedByUserId;
        this.decisionReason = normalize(decisionReason);
        this.decidedAt = decidedAt;
    }

    public void cancel(
            Long cancelledByUserId,
            String cancellationReason,
            OffsetDateTime cancelledAt
    ) {
        ensurePending();

        this.status = ProfileChangeRequestStatus.CANCELLED;
        this.decidedByUserId = cancelledByUserId;
        this.decisionReason = normalize(cancellationReason);
        this.decidedAt = cancelledAt;
    }

    public void expire(OffsetDateTime expiredAt) {
        ensurePending();

        this.status = ProfileChangeRequestStatus.EXPIRED;
        this.decidedAt = expiredAt;
    }

    private void ensurePending() {
        if (!isPending()) {
            throw new IllegalStateException(
                    "Only a pending profile change request can be changed"
            );
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}