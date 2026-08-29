package com.dawnrise.school.school.DTO;

import com.dawnrise.school.school.enums.ProvisioningStatus;
import com.dawnrise.school.school.enums.SchoolStatus;

import java.time.OffsetDateTime;

public class SchoolProvisioningResponse {

    private Long schoolId;
    private SchoolStatus schoolStatus;
    private ProvisioningStatus provisioningStatus;
    private int attemptCount;
    private String lastErrorSummary;
    private String lastErrorCode;
    private String lastErrorField;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public SchoolProvisioningResponse() {
    }

    public SchoolProvisioningResponse(
            Long schoolId,
            SchoolStatus schoolStatus,
            ProvisioningStatus provisioningStatus,
            int attemptCount,
            String lastErrorSummary,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                schoolId,
                schoolStatus,
                provisioningStatus,
                attemptCount,
                null,
                null,
                lastErrorSummary,
                createdAt,
                updatedAt
        );
    }

    public SchoolProvisioningResponse(
            Long schoolId,
            SchoolStatus schoolStatus,
            ProvisioningStatus provisioningStatus,
            int attemptCount,
            String lastErrorCode,
            String lastErrorField,
            String lastErrorSummary,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.schoolId = schoolId;
        this.schoolStatus = schoolStatus;
        this.provisioningStatus = provisioningStatus;
        this.attemptCount = attemptCount;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorField = lastErrorField;
        this.lastErrorSummary = lastErrorSummary;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getSchoolId() { return schoolId; }
    public SchoolStatus getSchoolStatus() { return schoolStatus; }
    public ProvisioningStatus getProvisioningStatus() { return provisioningStatus; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastErrorSummary() { return lastErrorSummary; }
    public String getLastErrorCode() {
        return lastErrorCode;
    }
    public String getLastErrorField() {
        return lastErrorField;
    }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
