package com.dawnrise.identity.studentguardian.entity;

import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipStatus;
import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipType;
import com.dawnrise.identity.studentguardian.exception.StudentGuardianRelationshipConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "student_guardian_relationships")
public class StudentGuardianRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "guardian_user_id", nullable = false)
    private Long guardianUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 30)
    private StudentGuardianRelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudentGuardianRelationshipStatus status;

    @Column(name = "primary_guardian", nullable = false)
    private boolean primaryGuardian;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "ended_by_user_id")
    private Long endedByUserId;

    @Column(name = "end_reason", length = 500)
    private String endReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudentGuardianRelationship() {
    }

    public StudentGuardianRelationship(
            Long organizationId,
            Long studentUserId,
            Long guardianUserId,
            StudentGuardianRelationshipType relationshipType,
            boolean primaryGuardian,
            Long createdByUserId,
            OffsetDateTime startedAt
    ) {
        requirePositive(organizationId, "Organization ID is required");
        requirePositive(studentUserId, "Student user ID is required");
        requirePositive(guardianUserId, "Guardian user ID is required");
        requirePositive(createdByUserId, "Creator user ID is required");

        if (studentUserId.equals(guardianUserId)) {
            throw new IllegalArgumentException(
                    "Student and guardian cannot be the same user"
            );
        }

        if (relationshipType == null) {
            throw new IllegalArgumentException(
                    "Relationship type is required"
            );
        }

        if (startedAt == null) {
            throw new IllegalArgumentException("Start time is required");
        }

        this.organizationId = organizationId;
        this.studentUserId = studentUserId;
        this.guardianUserId = guardianUserId;
        this.relationshipType = relationshipType;
        this.primaryGuardian = primaryGuardian;
        this.createdByUserId = createdByUserId;
        this.startedAt = startedAt;
        this.status = StudentGuardianRelationshipStatus.ACTIVE;
    }

    public void makePrimary() {
        ensureActive();
        this.primaryGuardian = true;
    }

    public void demotePrimary() {
        ensureActive();
        this.primaryGuardian = false;
    }

    public void end(
            Long endedByUserId,
            String endReason,
            OffsetDateTime endedAt
    ) {
        if (status == StudentGuardianRelationshipStatus.ENDED) {
            throw new StudentGuardianRelationshipConflictException(
                    "Relationship is already ended"
            );
        }

        requirePositive(endedByUserId, "Ending user ID is required");

        if (endedAt == null) {
            throw new IllegalArgumentException("End time is required");
        }

        String normalizedReason = normalizeReason(endReason);

        this.status = StudentGuardianRelationshipStatus.ENDED;
        this.endedByUserId = endedByUserId;
        this.endedAt = endedAt;
        this.endReason = normalizedReason;
    }

    private void ensureActive() {
        if (status != StudentGuardianRelationshipStatus.ACTIVE) {
            throw new StudentGuardianRelationshipConflictException(
                    "Relationship is not active"
            );
        }
    }

    private String normalizeReason(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    "End reason cannot be blank"
            );
        }

        return trimmed;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public Long getGuardianUserId() {
        return guardianUserId;
    }

    public StudentGuardianRelationshipType getRelationshipType() {
        return relationshipType;
    }

    public StudentGuardianRelationshipStatus getStatus() {
        return status;
    }

    public boolean isPrimaryGuardian() {
        return primaryGuardian;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public Long getEndedByUserId() {
        return endedByUserId;
    }

    public String getEndReason() {
        return endReason;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
