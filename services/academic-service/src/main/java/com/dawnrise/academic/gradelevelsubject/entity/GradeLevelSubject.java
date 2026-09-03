package com.dawnrise.academic.gradelevelsubject.entity;

import com.dawnrise.academic.gradelevelsubject.exception.InvalidGradeLevelSubjectException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "grade_level_subjects")
public class GradeLevelSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "mandatory", nullable = false)
    private Boolean mandatory;

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

    public GradeLevelSubject() {
    }

    public GradeLevelSubject(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long subjectId,
            Boolean mandatory,
            Integer displayOrder
    ) {
        if (organizationId == null || organizationId <= 0) {
            throw new InvalidGradeLevelSubjectException(
                    "Organization ID must be greater than zero"
            );
        }

        if (academicYearId == null || academicYearId <= 0) {
            throw new InvalidGradeLevelSubjectException(
                    "Academic year ID must be greater than zero"
            );
        }

        if (gradeLevelId == null || gradeLevelId <= 0) {
            throw new InvalidGradeLevelSubjectException(
                    "Grade level ID must be greater than zero"
            );
        }

        if (subjectId == null || subjectId <= 0) {
            throw new InvalidGradeLevelSubjectException(
                    "Subject ID must be greater than zero"
            );
        }

        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.subjectId = subjectId;

        applyDetails(mandatory, displayOrder);
    }

    public void updateDetails(
            Boolean mandatory,
            Integer displayOrder
    ) {
        applyDetails(mandatory, displayOrder);
    }

    private void applyDetails(
            Boolean mandatory,
            Integer displayOrder
    ) {
        if (mandatory == null) {
            throw new InvalidGradeLevelSubjectException(
                    "Mandatory value is required"
            );
        }

        if (displayOrder == null || displayOrder <= 0) {
            throw new InvalidGradeLevelSubjectException(
                    "Display order must be greater than zero"
            );
        }

        this.mandatory = mandatory;
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

    public Long getSubjectId() {
        return subjectId;
    }

    public Boolean getMandatory() {
        return mandatory;
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