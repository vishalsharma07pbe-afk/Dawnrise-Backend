package com.dawnrise.academic.results.entity;

import com.dawnrise.academic.results.enums.ResultSubjectSheetStatus;
import com.dawnrise.academic.results.exception.InvalidResultException;
import com.dawnrise.academic.results.exception.ResultConflictException;
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
@Table(name = "result_subject_sheets")
public class ResultSubjectSheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "examination_id", nullable = false)
    private Long examinationId;

    @Column(name = "scheduled_assessment_id", nullable = false)
    private Long scheduledAssessmentId;

    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "grade_level_subject_id", nullable = false)
    private Long gradeLevelSubjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResultSubjectSheetStatus status = ResultSubjectSheetStatus.DRAFT;

    @Column(name = "submitted_by_user_id")
    private Long submittedByUserId;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ResultSubjectSheet() {
    }

    public ResultSubjectSheet(long organizationId, long academicYearId,
                              long examinationId, long scheduledAssessmentId,
                              long gradeLevelId, long sectionId,
                              long gradeLevelSubjectId) {
        if (organizationId <= 0 || academicYearId <= 0 || examinationId <= 0
                || scheduledAssessmentId <= 0 || gradeLevelId <= 0
                || sectionId <= 0 || gradeLevelSubjectId <= 0) {
            throw new InvalidResultException("Result subject sheet IDs must be positive");
        }
        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.examinationId = examinationId;
        this.scheduledAssessmentId = scheduledAssessmentId;
        this.gradeLevelId = gradeLevelId;
        this.sectionId = sectionId;
        this.gradeLevelSubjectId = gradeLevelSubjectId;
    }

    public void requireEditable() {
        if (status != ResultSubjectSheetStatus.DRAFT
                && status != ResultSubjectSheetStatus.RETURNED) {
            throw new ResultConflictException("Only a draft or returned sheet can be edited");
        }
    }

    public void submit(long actorUserId) {
        requireEditable();
        validateActor(actorUserId);
        status = ResultSubjectSheetStatus.SUBMITTED;
        submittedByUserId = actorUserId;
        submittedAt = OffsetDateTime.now();
        reviewedByUserId = null;
        reviewedAt = null;
        reviewNote = null;
    }

    public void returnForCorrection(long actorUserId, String note) {
        requireSubmitted();
        validateActor(actorUserId);
        status = ResultSubjectSheetStatus.RETURNED;
        submittedByUserId = null;
        submittedAt = null;
        reviewedByUserId = actorUserId;
        reviewedAt = OffsetDateTime.now();
        reviewNote = normalizeNote(note);
    }

    public void approve(long actorUserId) {
        requireSubmitted();
        validateActor(actorUserId);
        status = ResultSubjectSheetStatus.APPROVED;
        reviewedByUserId = actorUserId;
        reviewedAt = OffsetDateTime.now();
        reviewNote = null;
    }

    private void requireSubmitted() {
        if (status != ResultSubjectSheetStatus.SUBMITTED) {
            throw new ResultConflictException("Only a submitted sheet can be reviewed");
        }
    }

    private static void validateActor(long actorUserId) {
        if (actorUserId <= 0) {
            throw new InvalidResultException("Actor user ID must be positive");
        }
    }

    private static String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        if (note.isBlank()) {
            throw new InvalidResultException("Review note cannot be blank");
        }
        String normalized = note.trim();
        if (normalized.length() > 500) {
            throw new InvalidResultException("Review note cannot exceed 500 characters");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getAcademicYearId() { return academicYearId; }
    public Long getExaminationId() { return examinationId; }
    public Long getScheduledAssessmentId() { return scheduledAssessmentId; }
    public Long getGradeLevelId() { return gradeLevelId; }
    public Long getSectionId() { return sectionId; }
    public Long getGradeLevelSubjectId() { return gradeLevelSubjectId; }
    public ResultSubjectSheetStatus getStatus() { return status; }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
