package com.dawnrise.identity.organization.provisioning.entity;

import com.dawnrise.identity.organization.provisioning.enums.ProvisioningRequestStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "organization_provisioning_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_org_provisioning_idempotency_key",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_org_provisioning_organization_id",
                        columnList = "organization_id"
                )
        }
)
public class OrganizationProvisioningRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 200
    )
    private String idempotencyKey;

    @Column(
            name = "organization_id",
            nullable = false
    )
    private Long organizationId;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvisioningRequestStatus status;

    @Column(
            name = "response_payload",
            columnDefinition = "TEXT"
    )
    private String responsePayload;

    @Column(
            name = "error_summary",
            length = 500
    )
    private String errorSummary;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(
            name = "updated_at",
            nullable = false
    )
    private OffsetDateTime updatedAt;

    protected OrganizationProvisioningRequest() {
    }

    public OrganizationProvisioningRequest(
            String idempotencyKey,
            Long organizationId,
            String requestHash
    ) {
        this.idempotencyKey = requireText(
                idempotencyKey,
                "Idempotency key is required"
        );

        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException(
                    "Organization ID must be positive"
            );
        }

        this.organizationId = organizationId;

        this.requestHash = requireText(
                requestHash,
                "Request hash is required"
        );

        this.status = ProvisioningRequestStatus.PROCESSING;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public ProvisioningRequestStatus getStatus() {
        return status;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markSucceeded(
            String responsePayload
    ) {
        ensureProcessing();

        this.responsePayload = requireText(
                responsePayload,
                "Provisioning response is required"
        );

        this.errorSummary = null;
        this.status = ProvisioningRequestStatus.SUCCEEDED;
        this.completedAt = OffsetDateTime.now();
    }

    public void markFailed(
            String errorSummary
    ) {
        ensureProcessing();

        this.errorSummary = requireText(
                errorSummary,
                "Provisioning error summary is required"
        );

        this.responsePayload = null;
        this.status = ProvisioningRequestStatus.FAILED;
        this.completedAt = OffsetDateTime.now();
    }

    public void restart() {
        if (status != ProvisioningRequestStatus.FAILED) {
            throw new IllegalStateException(
                    "Only a failed provisioning request can be restarted"
            );
        }

        this.status = ProvisioningRequestStatus.PROCESSING;
        this.responsePayload = null;
        this.errorSummary = null;
        this.completedAt = null;
    }

    private void ensureProcessing() {
        if (status != ProvisioningRequestStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only a processing request can be completed"
            );
        }
    }

    private static String requireText(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }
}