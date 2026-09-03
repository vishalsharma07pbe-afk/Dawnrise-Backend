package com.dawnrise.academic.subject.entity;

import com.dawnrise.academic.subject.exception.InvalidSubjectException;
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
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Subject() {
    }

    public Subject(
            Long organizationId,
            Long academicYearId,
            String code,
            String name,
            String description
    ) {
        if (organizationId == null || organizationId <= 0) {
            throw new InvalidSubjectException(
                    "Organization ID must be greater than zero"
            );
        }

        if (academicYearId == null || academicYearId <= 0) {
            throw new InvalidSubjectException(
                    "Academic year ID must be greater than zero"
            );
        }

        this.organizationId = organizationId;
        this.academicYearId = academicYearId;

        applyDetails(code, name, description);
    }

    public void updateDetails(
            String code,
            String name,
            String description
    ) {
        applyDetails(code, name, description);
    }

    private void applyDetails(
            String code,
            String name,
            String description
    ) {
        if (code == null || code.isBlank()) {
            throw new InvalidSubjectException(
                    "Subject code is required"
            );
        }

        String normalizedCode =
                code.trim().toUpperCase(Locale.ROOT);

        if (normalizedCode.length() > 50) {
            throw new InvalidSubjectException(
                    "Subject code cannot exceed 50 characters"
            );
        }

        if (name == null || name.isBlank()) {
            throw new InvalidSubjectException(
                    "Subject name is required"
            );
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > 120) {
            throw new InvalidSubjectException(
                    "Subject name cannot exceed 120 characters"
            );
        }

        String normalizedDescription = null;

        if (description != null) {
            if (description.isBlank()) {
                throw new InvalidSubjectException(
                        "Subject description cannot be blank"
                );
            }

            normalizedDescription = description.trim();

            if (normalizedDescription.length() > 500) {
                throw new InvalidSubjectException(
                        "Subject description cannot exceed 500 characters"
                );
            }
        }

        this.code = normalizedCode;
        this.name = normalizedName;
        this.description = normalizedDescription;
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

    public String getDescription() {
        return description;
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