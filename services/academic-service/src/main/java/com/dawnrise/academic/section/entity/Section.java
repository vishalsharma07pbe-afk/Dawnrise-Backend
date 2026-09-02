package com.dawnrise.academic.section.entity;

import com.dawnrise.academic.section.exception.InvalidSectionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Locale;

@Entity
@Table(name = "sections")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;

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

    public Section() {
    }

    public Section(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            String code,
            String name,
            Integer displayOrder
    ) {
        if (organizationId == null || organizationId <= 0) {
            throw new InvalidSectionException(
                    "Organization ID must be greater than zero"
            );
        }

        if (academicYearId == null || academicYearId <= 0) {
            throw new InvalidSectionException(
                    "Academic year ID must be greater than zero"
            );
        }

        if (gradeLevelId == null || gradeLevelId <= 0) {
            throw new InvalidSectionException(
                    "Grade level ID must be greater than zero"
            );
        }

        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;

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
            throw new InvalidSectionException(
                    "Section code is required"
            );
        }

        String normalizedCode =
                code.trim().toUpperCase(Locale.ROOT);

        if (normalizedCode.length() > 50) {
            throw new InvalidSectionException(
                    "Section code cannot exceed 50 characters"
            );
        }

        if (name == null || name.isBlank()) {
            throw new InvalidSectionException(
                    "Section name is required"
            );
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > 100) {
            throw new InvalidSectionException(
                    "Section name cannot exceed 100 characters"
            );
        }

        if (displayOrder == null || displayOrder <= 0) {
            throw new InvalidSectionException(
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

    public Long getGradeLevelId() {
        return gradeLevelId;
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