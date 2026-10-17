package com.dawnrise.academic.results.entity;

import com.dawnrise.academic.results.enums.ResultPublicationStatus;
import com.dawnrise.academic.results.exception.InvalidResultException;
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
@Table(name = "result_publications")
public class ResultPublication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "examination_id", nullable = false)
    private Long examinationId;

    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResultPublicationStatus status = ResultPublicationStatus.PUBLISHED;

    @Column(name = "published_by_user_id", nullable = false)
    private Long publishedByUserId;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ResultPublication() {
    }

    public ResultPublication(
            long organizationId,
            long academicYearId,
            long examinationId,
            long gradeLevelId,
            long sectionId,
            long publishedByUserId
    ) {
        if (organizationId <= 0 || academicYearId <= 0 || examinationId <= 0
                || gradeLevelId <= 0 || sectionId <= 0 || publishedByUserId <= 0) {
            throw new InvalidResultException("Result publication IDs must be positive");
        }
        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.examinationId = examinationId;
        this.gradeLevelId = gradeLevelId;
        this.sectionId = sectionId;
        this.publishedByUserId = publishedByUserId;
        this.publishedAt = OffsetDateTime.now();
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

    public Long getExaminationId() {
        return examinationId;
    }

    public Long getGradeLevelId() {
        return gradeLevelId;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public ResultPublicationStatus getStatus() {
        return status;
    }

    public Long getPublishedByUserId() {
        return publishedByUserId;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
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
