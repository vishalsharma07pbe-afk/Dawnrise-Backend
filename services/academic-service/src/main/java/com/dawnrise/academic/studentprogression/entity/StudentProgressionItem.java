package com.dawnrise.academic.studentprogression.entity;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import com.dawnrise.academic.studentprogression.exception.InvalidStudentProgressionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_progression_items")
public class StudentProgressionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false)
    private Long operationId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "source_enrollment_id", nullable = false)
    private Long sourceEnrollmentId;

    /*
     * External reference to the student user owned by identity-service.
     */
    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private StudentProgressionOutcome outcome;

    /*
     * Present only for PROMOTED and REPEATED outcomes.
     */
    @Column(name = "target_enrollment_id")
    private Long targetEnrollmentId;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Column(name = "note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public StudentProgressionItem() {
    }

    public StudentProgressionItem(
            Long operationId,
            Long organizationId,
            Long sourceEnrollmentId,
            Long studentUserId,
            StudentProgressionOutcome outcome,
            Long targetEnrollmentId,
            LocalDate effectiveOn,
            String note
    ) {
        requirePositive(operationId, "Operation ID");
        requirePositive(organizationId, "Organization ID");
        requirePositive(sourceEnrollmentId, "Source enrollment ID");
        requirePositive(studentUserId, "Student user ID");

        if (outcome == null) {
            throw new InvalidStudentProgressionException(
                    "Progression outcome is required"
            );
        }

        if (outcome == StudentProgressionOutcome.MANUAL_REVIEW) {
            throw new InvalidStudentProgressionException(
                    "A manual-review decision cannot be persisted as a confirmed progression item"
            );
        }

        if (effectiveOn == null) {
            throw new InvalidStudentProgressionException(
                    "Progression effective date is required"
            );
        }

        boolean createsTargetEnrollment =
                outcome == StudentProgressionOutcome.PROMOTED
                        || outcome == StudentProgressionOutcome.REPEATED;

        if (createsTargetEnrollment) {
            requirePositive(
                    targetEnrollmentId,
                    "Target enrollment ID"
            );
        } else if (targetEnrollmentId != null) {
            throw new InvalidStudentProgressionException(
                    "A target enrollment is not allowed for "
                            + outcome
            );
        }

        String normalizedNote = normalizeNote(note);

        this.operationId = operationId;
        this.organizationId = organizationId;
        this.sourceEnrollmentId = sourceEnrollmentId;
        this.studentUserId = studentUserId;
        this.outcome = outcome;
        this.targetEnrollmentId = targetEnrollmentId;
        this.effectiveOn = effectiveOn;
        this.note = normalizedNote;
    }

    private static void requirePositive(
            Long value,
            String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new InvalidStudentProgressionException(
                    fieldName + " must be greater than zero"
            );
        }
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }

        String normalizedNote = note.trim();

        if (normalizedNote.length() > 500) {
            throw new InvalidStudentProgressionException(
                    "Progression note cannot exceed 500 characters"
            );
        }

        return normalizedNote;
    }

    public Long getId() {
        return id;
    }

    public Long getOperationId() {
        return operationId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getSourceEnrollmentId() {
        return sourceEnrollmentId;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public StudentProgressionOutcome getOutcome() {
        return outcome;
    }

    public Long getTargetEnrollmentId() {
        return targetEnrollmentId;
    }

    public LocalDate getEffectiveOn() {
        return effectiveOn;
    }

    public String getNote() {
        return note;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}