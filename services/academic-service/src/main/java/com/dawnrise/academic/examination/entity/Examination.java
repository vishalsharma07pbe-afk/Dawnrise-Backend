package com.dawnrise.academic.examination.entity;

import com.dawnrise.academic.examination.enums.ExaminationStatus;
import com.dawnrise.academic.examination.exception.ExaminationConflictException;
import com.dawnrise.academic.examination.exception.InvalidExaminationException;
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
@Table(name = "examinations")
public class Examination {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExaminationStatus status = ExaminationStatus.DRAFT;

    @Column(name = "published_at")
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

    protected Examination() {}

    public Examination(long organizationId, long academicYearId, String name) {
        if (organizationId <= 0 || academicYearId <= 0) {
            throw new InvalidExaminationException("Organization and academic year IDs must be positive");
        }
        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.name = normalizeName(name);
    }

    public void requireDraft() {
        if (status != ExaminationStatus.DRAFT) {
            throw new ExaminationConflictException("Only a draft examination can be edited");
        }
    }

    public void rename(String name) {
        requireDraft();
        this.name = normalizeName(name);
    }

    public void publish() {
        requireDraft();
        status = ExaminationStatus.PUBLISHED;
        publishedAt = OffsetDateTime.now();
    }

    public void cancel() {
        if (status == ExaminationStatus.CANCELLED) {
            throw new ExaminationConflictException("Examination is already cancelled");
        }
        status = ExaminationStatus.CANCELLED;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidExaminationException("Examination name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw new InvalidExaminationException("Examination name cannot exceed 120 characters");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getAcademicYearId() { return academicYearId; }
    public String getName() { return name; }
    public ExaminationStatus getStatus() { return status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
