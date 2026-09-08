package com.dawnrise.academic.academicyearrollover.entity;

import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "academic_year_structure_rollover_operations")
public class AcademicYearStructureRolloverOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "source_academic_year_id", nullable = false)
    private Long sourceAcademicYearId;

    @Column(name = "target_academic_year_id", nullable = false)
    private Long targetAcademicYearId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 71)
    private String requestHash;

    @Column(name = "preview_fingerprint", nullable = false, length = 71)
    private String previewFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RolloverOperationStatus status;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public AcademicYearStructureRolloverOperation() {
    }

    public AcademicYearStructureRolloverOperation(
            Long organizationId,
            Long sourceAcademicYearId,
            Long targetAcademicYearId,
            String idempotencyKey,
            String requestHash,
            String previewFingerprint,
            Long requestedByUserId
    ) {
        this.organizationId = organizationId;
        this.sourceAcademicYearId = sourceAcademicYearId;
        this.targetAcademicYearId = targetAcademicYearId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.previewFingerprint = previewFingerprint;
        this.requestedByUserId = requestedByUserId;
        this.status = RolloverOperationStatus.PENDING;
    }

    public void markRunning() {
        this.status = RolloverOperationStatus.RUNNING;
        this.startedAt = OffsetDateTime.now();
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markSucceeded(String resultJson) {
        this.status = RolloverOperationStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.completedAt = OffsetDateTime.now();
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markTerminalFailure(
            RolloverOperationStatus status,
            String failureCode,
            String failureMessage
    ) {
        this.status = status;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.completedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getSourceAcademicYearId() { return sourceAcademicYearId; }
    public Long getTargetAcademicYearId() { return targetAcademicYearId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getPreviewFingerprint() { return previewFingerprint; }
    public RolloverOperationStatus getStatus() { return status; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getResultJson() { return resultJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
