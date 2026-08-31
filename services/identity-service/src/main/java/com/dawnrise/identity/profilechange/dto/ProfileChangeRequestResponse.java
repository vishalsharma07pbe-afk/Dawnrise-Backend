package com.dawnrise.identity.profilechange.dto;

import com.dawnrise.identity.profilechange.enums.ProfileChangeRequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ProfileChangeRequestResponse {

    private final UUID requestId;
    private final String requestedByName;
    private final String decidedByName;

    private final String originalFirstName;
    private final String originalMiddleName;
    private final String originalLastName;
    private final String originalEmail;
    private final String originalPhone;

    private final String proposedFirstName;
    private final String proposedMiddleName;
    private final String proposedLastName;
    private final String proposedEmail;
    private final String proposedPhone;

    private final String reason;
    private final ProfileChangeRequestStatus status;

    private final String decisionReason;
    private final OffsetDateTime decidedAt;
    private final OffsetDateTime expiresAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public ProfileChangeRequestResponse(
            UUID requestId,
            String requestedByName,
            String decidedByName,
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
            ProfileChangeRequestStatus status,
            String decisionReason,
            OffsetDateTime decidedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.requestId = requestId;
        this.requestedByName = requestedByName;
        this.decidedByName = decidedByName;
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
        this.status = status;
        this.decisionReason = decisionReason;
        this.decidedAt = decidedAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public String getDecidedByName() {
        return decidedByName;
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
}