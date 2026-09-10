package com.dawnrise.academic.studentprogression.entity;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.exception.InvalidStudentProgressionException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import java.time.ZoneOffset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "student_progression_operations")
public class StudentProgressionOperation {

    private static final String SHA_256_PATTERN =
            "^sha256:[0-9a-f]{64}$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "source_academic_year_id", nullable = false)
    private Long sourceAcademicYearId;

    @Column(name = "target_academic_year_id", nullable = false)
    private Long targetAcademicYearId;

    @Column(name = "batch_label", nullable = false, length = 100)
    private String batchLabel;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 71)
    private String requestHash;

    @Column(name = "preview_fingerprint", nullable = false, length = 71)
    private String previewFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StudentProgressionOperationStatus status;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "total_items", nullable = false)
    private Integer totalItems;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    /*
     * PostgreSQL JSONB must be mapped with Hibernate JSON typing.
     * Without @JdbcTypeCode, Hibernate binds the String as VARCHAR.
     */
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

    public StudentProgressionOperation() {
    }

    public StudentProgressionOperation(
            Long organizationId,
            Long sourceAcademicYearId,
            Long targetAcademicYearId,
            String batchLabel,
            String idempotencyKey,
            String requestHash,
            String previewFingerprint,
            Long requestedByUserId,
            Integer totalItems
    ) {
        requirePositive(organizationId, "Organization ID");
        requirePositive(sourceAcademicYearId, "Source academic year ID");
        requirePositive(targetAcademicYearId, "Target academic year ID");
        requirePositive(requestedByUserId, "Requested-by user ID");

        if (sourceAcademicYearId.equals(targetAcademicYearId)) {
            throw new InvalidStudentProgressionException(
                    "Source and target academic years must be different"
            );
        }

        String normalizedBatchLabel = normalizeRequired(
                batchLabel,
                "Batch label"
        );

        if (normalizedBatchLabel.length() > 100) {
            throw new InvalidStudentProgressionException(
                    "Batch label cannot exceed 100 characters"
            );
        }

        String normalizedIdempotencyKey = normalizeRequired(
                idempotencyKey,
                "Idempotency key"
        );

        if (normalizedIdempotencyKey.length() > 128) {
            throw new InvalidStudentProgressionException(
                    "Idempotency key cannot exceed 128 characters"
            );
        }

        validateHash(requestHash, "Request hash");
        validateHash(previewFingerprint, "Preview fingerprint");

        if (totalItems == null
                || totalItems < 1
                || totalItems > 2000) {
            throw new InvalidStudentProgressionException(
                    "Total items must be between 1 and 2000"
            );
        }

        this.organizationId = organizationId;
        this.sourceAcademicYearId = sourceAcademicYearId;
        this.targetAcademicYearId = targetAcademicYearId;
        this.batchLabel = normalizedBatchLabel;
        this.idempotencyKey = normalizedIdempotencyKey;
        this.requestHash = requestHash;
        this.previewFingerprint = previewFingerprint;
        this.status = StudentProgressionOperationStatus.PENDING;
        this.requestedByUserId = requestedByUserId;
        this.totalItems = totalItems;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getSourceAcademicYearId() {
        return sourceAcademicYearId;
    }

    public Long getTargetAcademicYearId() {
        return targetAcademicYearId;
    }

    public String getBatchLabel() {
        return batchLabel;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getPreviewFingerprint() {
        return previewFingerprint;
    }

    public StudentProgressionOperationStatus getStatus() {
        return status;
    }

    public Long getRequestedByUserId() {
        return requestedByUserId;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getResultJson() {
        return resultJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markRunning() {
        ensureStatus(StudentProgressionOperationStatus.PENDING);

        this.status = StudentProgressionOperationStatus.RUNNING;
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markSucceeded(String resultJson) {
        ensureStatus(StudentProgressionOperationStatus.RUNNING);

        if (resultJson == null || resultJson.isBlank()) {
            throw new InvalidStudentProgressionException(
                    "Successful operation result JSON is required"
            );
        }

        this.status = StudentProgressionOperationStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.failureCode = null;
        this.failureMessage = null;
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markFailed(
            String failureCode,
            String failureMessage
    ) {
        markUnsuccessful(
                StudentProgressionOperationStatus.FAILED,
                failureCode,
                failureMessage
        );
    }

    public void markStalePreview(
            String failureCode,
            String failureMessage
    ) {
        markUnsuccessful(
                StudentProgressionOperationStatus.STALE_PREVIEW,
                failureCode,
                failureMessage
        );
    }

    public void markConflicted(
            String failureCode,
            String failureMessage
    ) {
        markUnsuccessful(
                StudentProgressionOperationStatus.CONFLICTED,
                failureCode,
                failureMessage
        );
    }

    private void markUnsuccessful(
            StudentProgressionOperationStatus targetStatus,
            String failureCode,
            String failureMessage
    ) {
        ensureStatus(StudentProgressionOperationStatus.RUNNING);

        String normalizedCode = normalizeRequired(
                failureCode,
                "Failure code"
        );

        String normalizedMessage = normalizeRequired(
                failureMessage,
                "Failure message"
        );

        if (normalizedCode.length() > 80) {
            throw new InvalidStudentProgressionException(
                    "Failure code cannot exceed 80 characters"
            );
        }

        if (normalizedMessage.length() > 500) {
            throw new InvalidStudentProgressionException(
                    "Failure message cannot exceed 500 characters"
            );
        }

        this.status = targetStatus;
        this.failureCode = normalizedCode;
        this.failureMessage = normalizedMessage;
        this.resultJson = null;
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    private void ensureStatus(
            StudentProgressionOperationStatus expectedStatus
    ) {
        if (status != expectedStatus) {
            throw new StudentProgressionConflictException(
                    "Progression operation must be "
                            + expectedStatus
                            + " but is "
                            + status
            );
        }
    }

    private static void requirePositive(
            Long value,
            String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new InvalidStudentProgressionException(
                    fieldName + " must be greater than zero"
            );
        }
    }

    private static String normalizeRequired(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new InvalidStudentProgressionException(
                    fieldName + " is required"
            );
        }

        return value.trim();
    }

    private static void validateHash(
            String value,
            String fieldName
    ) {
        if (value == null || !value.matches(SHA_256_PATTERN)) {
            throw new InvalidStudentProgressionException(
                    fieldName + " must use the sha256:<64 lowercase hex characters> format"
            );
        }
    }
}