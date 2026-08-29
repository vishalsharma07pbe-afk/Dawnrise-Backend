package com.dawnrise.identity.organization.entity;

import com.dawnrise.identity.organization.enums.OrganizationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "organizations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_organizations_school_code",
                        columnNames = "school_code"
                )
        }
)
public class Organization {

    @Id
    @Column(nullable = false)
    private Long id;

    @Column(
            name = "school_code",
            nullable = false,
            length = 50
    )
    private String schoolCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrganizationStatus status;

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

    protected Organization() {
    }

    public Organization(
            Long id,
            String schoolCode,
            String name,
            String email
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Organization ID must be positive"
            );
        }

        this.id = id;
        this.schoolCode = requireText(
                schoolCode,
                "School code is required"
        );

        this.name = requireText(
                name,
                "Organization name is required"
        );

        this.email = normalizeOptionalText(email);
        this.status = OrganizationStatus.PROVISIONING;
    }

    public Long getId() {
        return id;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            String name,
            String email
    ) {
        this.name = requireText(
                name,
                "Organization name is required"
        );

        this.email = normalizeOptionalText(email);
    }

    public void completeProvisioning() {
        if (status == OrganizationStatus.ACTIVE) {
            return;
        }

        if (status != OrganizationStatus.PROVISIONING) {
            throw new IllegalStateException(
                    "Only a provisioning organization can be activated"
            );
        }

        this.status = OrganizationStatus.ACTIVE;
    }

    public void deactivate() {
        if (status == OrganizationStatus.INACTIVE) {
            return;
        }

        this.status = OrganizationStatus.INACTIVE;
    }

    public void reactivate() {
        if (status == OrganizationStatus.ACTIVE) {
            return;
        }

        if (status != OrganizationStatus.INACTIVE) {
            throw new IllegalStateException(
                    "Only an inactive organization can be reactivated"
            );
        }

        this.status = OrganizationStatus.ACTIVE;
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

    private static String normalizeOptionalText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}