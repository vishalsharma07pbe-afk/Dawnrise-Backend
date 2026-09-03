package com.dawnrise.academic.studentenrollment.entity;

import com.dawnrise.academic.studentenrollment.enums.StudentEnrollmentStatus;
import com.dawnrise.academic.studentenrollment.exception.InvalidStudentEnrollmentException;
import com.dawnrise.academic.studentenrollment.exception.StudentEnrollmentConflictException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;

@Entity
@Table(name = "student_enrollments")
public class StudentEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "roll_number", nullable = false, length = 30)
    private String rollNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentEnrollmentStatus status =
            StudentEnrollmentStatus.ENROLLED;

    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public StudentEnrollment() {
    }

    public StudentEnrollment(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            Long studentUserId,
            String rollNumber,
            LocalDate enrolledOn
    ) {
        if (organizationId == null || organizationId <= 0) {
            throw new InvalidStudentEnrollmentException(
                    "Organization ID must be greater than zero"
            );
        }

        if (academicYearId == null || academicYearId <= 0) {
            throw new InvalidStudentEnrollmentException(
                    "Academic year ID must be greater than zero"
            );
        }

        if (gradeLevelId == null || gradeLevelId <= 0) {
            throw new InvalidStudentEnrollmentException(
                    "Grade level ID must be greater than zero"
            );
        }

        if (sectionId == null || sectionId <= 0) {
            throw new InvalidStudentEnrollmentException(
                    "Section ID must be greater than zero"
            );
        }

        if (studentUserId == null || studentUserId <= 0) {
            throw new InvalidStudentEnrollmentException(
                    "Student user ID must be greater than zero"
            );
        }

        if (enrolledOn == null) {
            throw new InvalidStudentEnrollmentException(
                    "Enrollment date is required"
            );
        }

        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.sectionId = sectionId;
        this.studentUserId = studentUserId;
        this.enrolledOn = enrolledOn;

        applyRollNumber(rollNumber);
    }

    public void updateRollNumber(String rollNumber) {
        ensureCurrentlyEnrolled();
        applyRollNumber(rollNumber);
    }

    public void markTransferred(LocalDate endedOn) {
        endEnrollment(
                StudentEnrollmentStatus.TRANSFERRED,
                endedOn
        );
    }

    public void markWithdrawn(LocalDate endedOn) {
        endEnrollment(
                StudentEnrollmentStatus.WITHDRAWN,
                endedOn
        );
    }

    public void markCompleted(LocalDate endedOn) {
        endEnrollment(
                StudentEnrollmentStatus.COMPLETED,
                endedOn
        );
    }

    private void endEnrollment(
            StudentEnrollmentStatus targetStatus,
            LocalDate endedOn
    ) {
        ensureCurrentlyEnrolled();

        if (endedOn == null) {
            throw new InvalidStudentEnrollmentException(
                    "Enrollment end date is required"
            );
        }

        if (endedOn.isBefore(enrolledOn)) {
            throw new InvalidStudentEnrollmentException(
                    "Enrollment end date cannot be before enrollment date"
            );
        }

        this.status = targetStatus;
        this.endedOn = endedOn;
    }

    private void ensureCurrentlyEnrolled() {
        if (status != StudentEnrollmentStatus.ENROLLED) {
            throw new StudentEnrollmentConflictException(
                    "Only a current enrollment can be modified"
            );
        }
    }

    private void applyRollNumber(String rollNumber) {
        if (rollNumber == null || rollNumber.isBlank()) {
            throw new InvalidStudentEnrollmentException(
                    "Roll number is required"
            );
        }

        String normalizedRollNumber =
                rollNumber.trim().toUpperCase(Locale.ROOT);

        if (normalizedRollNumber.length() > 30) {
            throw new InvalidStudentEnrollmentException(
                    "Roll number cannot exceed 30 characters"
            );
        }

        this.rollNumber = normalizedRollNumber;
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

    public Long getSectionId() {
        return sectionId;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public StudentEnrollmentStatus getStatus() {
        return status;
    }

    public LocalDate getEnrolledOn() {
        return enrolledOn;
    }

    public LocalDate getEndedOn() {
        return endedOn;
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