package com.dawnrise.identity.studentguardian.dto;

import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipStatus;
import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipType;

import java.time.OffsetDateTime;

public class StudentGuardianRelationshipResponse {

    private Long id;
    private Long organizationId;
    private StudentGuardianUserSummaryResponse student;
    private StudentGuardianUserSummaryResponse guardian;
    private StudentGuardianRelationshipType relationshipType;
    private StudentGuardianRelationshipStatus status;
    private boolean primaryGuardian;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Long createdByUserId;
    private Long endedByUserId;
    private String endReason;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public StudentGuardianRelationshipResponse() {
    }

    public StudentGuardianRelationshipResponse(
            Long id,
            Long organizationId,
            StudentGuardianUserSummaryResponse student,
            StudentGuardianUserSummaryResponse guardian,
            StudentGuardianRelationshipType relationshipType,
            StudentGuardianRelationshipStatus status,
            boolean primaryGuardian,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            Long createdByUserId,
            Long endedByUserId,
            String endReason,
            Long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.student = student;
        this.guardian = guardian;
        this.relationshipType = relationshipType;
        this.status = status;
        this.primaryGuardian = primaryGuardian;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdByUserId = createdByUserId;
        this.endedByUserId = endedByUserId;
        this.endReason = endReason;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public StudentGuardianUserSummaryResponse getStudent() { return student; }
    public StudentGuardianUserSummaryResponse getGuardian() { return guardian; }
    public StudentGuardianRelationshipType getRelationshipType() { return relationshipType; }
    public StudentGuardianRelationshipStatus getStatus() { return status; }
    public boolean isPrimaryGuardian() { return primaryGuardian; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getEndedByUserId() { return endedByUserId; }
    public String getEndReason() { return endReason; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
