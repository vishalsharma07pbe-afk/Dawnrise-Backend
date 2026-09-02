package com.dawnrise.academic.gradelevel.entity;

import com.dawnrise.academic.gradelevel.exception.InvalidGradeLevelException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Locale;

@Entity
@Table(name = "grade_levels")
public class GradeLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public GradeLevel() {
    }

    public GradeLevel(
            Long organizationId,
            Long academicYearId,
            String code,
            String name,
            Integer displayOrder
    ) {
        if (organizationId == null || organizationId <= 0) {
            throw new InvalidGradeLevelException(
                    "Organization ID must be greater than zero"
            );
        }

        if (academicYearId == null || academicYearId <= 0) {
            throw new InvalidGradeLevelException(
                    "Academic year ID must be greater than zero"
            );
        }

        this.organizationId = organizationId;
        this.academicYearId = academicYearId;

        applyDetails(code, name, displayOrder);
    }

    public void updateDetails(
            String code,
            String name,
            Integer displayOrder
    ) {
        applyDetails(code, name, displayOrder);
    }

    private void applyDetails(
            String code,
            String name,
            Integer displayOrder
    ) {
        if (code == null || code.isBlank()) {
            throw new InvalidGradeLevelException(
                    "Grade level code is required"
            );
        }

        String normalizedCode =
                code.trim().toUpperCase(Locale.ROOT);

        if (normalizedCode.length() > 50) {
            throw new InvalidGradeLevelException(
                    "Grade level code cannot exceed 50 characters"
            );
        }

        if (!normalizedCode.matches("[A-Z0-9_-]+")) {
            throw new InvalidGradeLevelException(
                    "Grade level code can contain only letters, numbers, hyphens, and underscores"
            );
        }

        if (name == null || name.isBlank()) {
            throw new InvalidGradeLevelException(
                    "Grade level name is required"
            );
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > 100) {
            throw new InvalidGradeLevelException(
                    "Grade level name cannot exceed 100 characters"
            );
        }

        if (displayOrder == null || displayOrder <= 0) {
            throw new InvalidGradeLevelException(
                    "Display order must be greater than zero"
            );
        }

        this.code = normalizedCode;
        this.name = normalizedName;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getAcademicYearId() {
        return academicYearId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
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
